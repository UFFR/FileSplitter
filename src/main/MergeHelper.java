package main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Scanner;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

/**
 * Class to merge chunks back into the original file.
 * @author UFFR
 *
 */
public class MergeHelper
{
	private final Path inputPath, outputPath;
	private final SplitSummary summary;
	private final Scanner scanner;
	private final MessageDigest inputDigest = Main.digestSupplier(), outputDigest = Main.digestSupplier();
	/**
	 * Construct a helper
	 * @param inputPath The path with the chunks.
	 * @param outputPath The path for the source file.
	 * @param summary The summary file to use.
	 * @param scanner Console input scanner for confirmations.
	 */
	public MergeHelper(Path inputPath, Path outputPath, SplitSummary summary, Scanner scanner)
	{
		this.inputPath = inputPath;
		this.outputPath = outputPath;
		this.summary = summary;
		this.scanner = scanner;
	}
	
	/**
	 * Begin the merge process.
	 * @throws IOException If any I/O exception occurs during the process.
	 */
	public void execute() throws IOException
	{
		final ProgressBarBuilder barBuilder = new ProgressBarBuilder()
				.setUnit("MB", Main.MB)
				.showSpeed()
				.setInitialMax(summary.getTotalSize())
				.setTaskName("Merging...");
		
		try (final OutputStream fileStream = Files.newOutputStream(outputPath.resolve(summary.getFilename()), StandardOpenOption.CREATE_NEW);
				final OutputStream outputStream = new DigestOutputStream(ProgressBar.wrap(fileStream, barBuilder), outputDigest);
				final ProgressBar inputBar = barBuilder.setTaskName("Reading...").setInitialMax(summary.getChunkSize()).build())
		{
			int index = 0;
			long processed = 0;
			
			while (processed < summary.getTotalSize() && index++ < summary.getChunkAmount())
				processed += readChunk(index, index == summary.getChunkAmount() ? Main.remainderBytes(summary.getTotalSize(), summary.getChunkSize()) : summary.getChunkSize(), outputStream, inputBar);
			
			final byte[] totalSum = outputDigest.digest();
			if (!MessageDigest.isEqual(summary.getTotalFileChecksum(), totalSum))
			{
				System.err.println("WARNING: Final merged file checksum mismatch, most likely corrupted!");
				System.err.println("Expected: [" + summary.getTotalFileChecksumHex() + "], but got: [" + Main.bytesToHex(totalSum) + "].");
			}
		}
	}
	
	/**
	 * Merge an individual chunk.
	 * @param index The chunk's index.
	 * @param expectedSize The size the chunk should be.
	 * @param destination The output merged file's stream.
	 * @param bar The progress bar to note progress to.
	 * @return The amount of bytes read and merged, should the the same as the size.
	 * @throws IOException If any exception occurs during the process.
	 */
	@SuppressWarnings("resource")
	public long readChunk(int index, long expectedSize, OutputStream destination, ProgressBar bar) throws IOException
	{
		// Reinitialize progress bar
		bar.reset().maxHint(expectedSize).setExtraMessage(" Chunk: #" + index + '/' + summary.getChunkAmount());
		bar.refresh();
		final Path chunkPath = Paths.get(inputPath.getParent().toString(), summary.getFilename() + '.' + index + ".part");
		final long chunkSize = Files.size(chunkPath);
		// In case the size is unexpected, confirm with user
		if (chunkSize != expectedSize)
		{
			System.err.printf("WARNING: Chunk #%s has a size of %s B instead of the expected %s B!\n", index, chunkSize, expectedSize).flush();
			System.err.println("Continue anyway? (boolean)");
			if (!scanner.nextBoolean())
				Main.cancel();
		}
		
		long processed = 0;
		try (final InputStream inputStream = new DigestInputStream(Files.newInputStream(chunkPath), inputDigest))
		{
//			System.out.println("Merging chunk #" + index + '/' + summary.getChunkAmount() + "...");
			
			while (processed < expectedSize)
			{
				final int bufferSize = (int) Math.min(Main.BUFFER, chunkSize - processed);
				final byte[] buffer = new byte[bufferSize];
				
				inputStream.read(buffer);
				destination.write(buffer);
				
				bar.stepBy(bufferSize).refresh();
				
				processed += bufferSize;
			}
		}
		
		// Confirm with user in case of checksum mismatch
		final byte[] chunkSum = inputDigest.digest();
		if (!MessageDigest.isEqual(summary.getChecksum(chunkPath), chunkSum))
		{
			System.err.println("WARNING: Checksum mismatch on chunk #" + index + ", chunk most likely corrupted!");
			System.err.println("Expected: [" + summary.getHexChunksum(chunkPath) + "], but got: [" + Main.bytesToHex(chunkSum) + "].");
			System.err.println("Continue anyway? (boolean)");
			if (!scanner.nextBoolean())
				Main.cancel();
		}
//		System.out.println("Merged chunk #" + index + '/' + summary.getChunkAmount() + '.');
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
