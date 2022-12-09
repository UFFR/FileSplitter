package main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

/**
 * Class to split the file into chunks.
 * @author UFFR
 *
 */
public class SplitHelper
{
	private final Path sourceFile, outputPath;
	private final SplitSummary summary;
	private final MessageDigest inputDigest = Main.digestSupplier(), outputDigest = Main.digestSupplier();
	/**
	 * Construct a helper.
	 * @param sourceFile The source file to split.
	 * @param outputPath Path to export the split chunks.
	 * @param summary The summary file to use.
	 */
	public SplitHelper(Path sourceFile, Path outputPath, SplitSummary summary)
	{
		this.sourceFile = sourceFile;
		this.outputPath = outputPath;
		this.summary = summary;
	}
	
	/**
	 * Begin the split process.
	 * @throws IOException If any I/O exception occurs during the process.
	 */
	public void execute() throws IOException
	{
		// Failsafe
		if (summary.getChunkAmount() <= 0)
			throw new IllegalStateException("Illegal chunk amount detected!");
		
		final ProgressBarBuilder barBuilder = new ProgressBarBuilder()
				.setUnit("MB", Main.MB)
				.showSpeed()
				.setInitialMax(summary.getTotalSize())
				.setTaskName("Splitting...");
		
		// Create parent directories if they don't exist
		Files.createDirectories(outputPath);
		
		try (final InputStream fileStream = Files.newInputStream(sourceFile);
				final InputStream inputStream = new DigestInputStream(ProgressBar.wrap(fileStream, barBuilder), inputDigest);
				final ProgressBar outputBar = barBuilder.setTaskName("Writing...").setInitialMax(summary.getChunkSize()).build())
		{
			final long sourceSize = Files.size(sourceFile);
			long read = 0;
			int index = 1;
			
			while (read < sourceSize)
				read += writeChunk(index++, Math.min(summary.getChunkSize(), sourceSize - read), inputStream, outputBar); // Should always be the same as the chunk size, but done for shorter code.
			
			// Register source file checksum
			summary.setTotalFileChecksum(inputDigest.digest());
		}
	}
	
	/**
	 * Write an individual chunk.
	 * @param index The chunk's index.
	 * @param size Size of the chunk.
	 * @param source The source file's stream.
	 * @param bar The progress bar to note progress to.
	 * @return The amount of bytes written, should be the same as the specified size.
	 * @throws IOException If any exception occurs during the process.
	 */
	@SuppressWarnings("resource")
	private long writeChunk(int index, long size, InputStream source, ProgressBar bar) throws IOException
	{
		// Reinitialize progress bar
		bar.reset().maxHint(size).setExtraMessage(" Chunk #: " + index + '/' + summary.getChunkAmount());
		bar.refresh();
		final Path chunkPath = outputPath.resolve(sourceFile + "." + index + ".part");
		long processed = 0;
		try (final OutputStream outputStream = new DigestOutputStream(Files.newOutputStream(chunkPath, StandardOpenOption.CREATE_NEW), outputDigest))
		{
			// Note to user
//			System.out.println("Writing chunk #" + index + '/' + summary.getChunkAmount() + "...");
			
			while (processed < size)
			{
				final int bufferSize = (int) Math.min(Main.BUFFER, size - processed);
				final byte[] buffer = new byte[bufferSize];
				
				source.read(buffer);
				outputStream.write(buffer);
				
				bar.stepBy(bufferSize).refresh();
				
				processed += bufferSize;
			}
			// Ensure all bytes are written
			outputStream.flush();
		}
		
		// Add to registry and note to user
		summary.addChunkPath(chunkPath, outputDigest.digest());
//		System.out.println("Wrote chunk #" + index + '/' + summary.getChunkAmount() + '.');
		return processed;
	}
	
	@Override
	protected void finalize() throws Throwable
	{
		super.finalize();
		inputDigest.reset();
		outputDigest.reset();
	}

}
