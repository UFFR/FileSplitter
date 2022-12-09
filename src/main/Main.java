package main;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main
{
	// Store version and summary file header
	public static final String VERSION = "v1.0.0b", MIME_TYPE = "application/j-file-splitter-summary";
	private static final Options OPTIONS = new Options();
	private static final CommandLineParser PARSER = new DefaultParser();
	private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
	private static final String[] DEFAULT_SIZE_PARAM = {"10", "MB"};
	static final short KB = 1024;
	static final int MB = KB * 1024, GB = MB * 1024, BUFFER = MB * 8;
	static
	{
		// Initialize command line options
		final OptionGroup group = new OptionGroup();
		group.addOption(Option.builder("h").longOpt("help").desc("Print the help screen.").build());
		group.addOption(Option.builder("p").longOpt("path").desc("Path of the file to split or the split summary.").required().hasArg().argName("input path").optionalArg(false).build());
		OPTIONS.addOption(Option.builder("e").longOpt("export").desc("Path to export the split or merged file(s). Defaults to the input path.").hasArg().argName("export path").optionalArg(false).build());
		OPTIONS.addOption(Option.builder("s").longOpt("size").desc("Size of each chunk and its unit (KB - GB). Max size is the 2^31-1 bytes and defaults to 10 MB.").valueSeparator(':').hasArgs().argName("size:unit").numberOfArgs(2).optionalArg(false).build());
		OPTIONS.addOption(Option.builder("m").longOpt("merge").desc("Merge chunks instead of splitting into them.").build());
		OPTIONS.addOptionGroup(group);
	}
	// Define working variables
	private static Path inputPath, outputPath;
	public static void main(String[] args)
	{
		// Define for later definition
		final long startTime;
		// Resource and to handle exceptions
		try (final Scanner scanner = new Scanner(System.in))
		{
			final CommandLine commandLine = PARSER.parse(OPTIONS, args);
			if (commandLine.hasOption('h'))
			{
				HELP_FORMATTER.printHelp("Requires specified file path.", "FileSplitter " + VERSION, OPTIONS, "Report bugs to the GitHub at: https://github.com/UFFR/FileSplitter", true);
				System.exit(0);
			}
			// Get specified input path or use running path
			inputPath = Paths.get(commandLine.getOptionValue('p', System.getProperty("user.dir")));
			// Get specified output path or use input's parent (assumes a file)
			outputPath = commandLine.hasOption('e') ? Paths.get(commandLine.getOptionValue('e')) : inputPath.toAbsolutePath().getParent();
			// If in merge mode
			if (commandLine.hasOption('m'))
			{
				// Read input and extract summary
				final ObjectInputStream objectInputStream = new ObjectInputStream(Files.newInputStream(inputPath));
				final SplitSummary summary = (SplitSummary) objectInputStream.readObject();
				objectInputStream.close();
				// Check for possible errors and report
				summary.checkForErrors();
				System.out.println("Output file will be: [" + summary.getFilename() + "].");
				System.out.println("Reported total file size is: " + summary.getTotalSize() + " bytes.");
				System.out.println("Reported chunk size is: " + summary.getChunkSize() + " bytes.");
				System.out.println("Reported total chunk amount is: " + summary.getChunkAmount() + ".\nNo errors detected in summary file.");
				// Display to user for confirmation
				System.out.println("Recognized input path as [" + inputPath.toAbsolutePath() + "], output path as [" + outputPath.toAbsolutePath() + "]. Continue? (boolean)");
				if (scanner.nextBoolean())
				{
					System.out.println("Beginning operation...");
					// Set starting time
					startTime = System.currentTimeMillis();
					
					// Begin merge process
					new MergeHelper(inputPath, outputPath, summary, scanner).execute();
					
					// Completed successfully
					System.out.println("\nDone!\n");
					// Report operation time
					System.out.println(timeFromMillis(System.currentTimeMillis() - startTime));
				} else
					cancel();
			} else // If split mode
			{
				// Get chunk size
				final String[] size = commandLine.hasOption('s') ? commandLine.getOptionValues('s') : DEFAULT_SIZE_PARAM;
				// Convert to usable numbers and get total file size
				final long chunkSize = Long.parseLong(size[0]) * magnitudeFromName(size[1]), totalSize = Files.size(inputPath);
				// Calculate total chunks
				final int totalChunks = chunkAmount(totalSize, chunkSize);
				// In case of error or invalid input
				if (chunkSize < 0)
				{
					System.err.println("Invalid chunk size inputted!");
					System.exit(1);
				}
				// Display to user for confirmation
				System.out.println("Recognized input path as [" + inputPath.toAbsolutePath() + "], output path as [" + outputPath.toAbsolutePath() + "], chunk size as ~" + roundBin(chunkSize) + ", making " + totalChunks + " total chunks. Continue? (boolean)");
				if (scanner.nextBoolean())
				{
					// Summary file path
					final Path sumPath = outputPath.resolve(inputPath.getFileName() + ".sum");
					System.out.println("Beginning operation...");
					// Set starting time
					startTime = System.currentTimeMillis();
					// Initialize summary
					final SplitSummary summary = new SplitSummary(totalSize, chunkSize, inputPath.getFileName().toString());
					
					// Begin split process
					new SplitHelper(inputPath, outputPath, summary).execute();
					
					// Completed successfully
					System.out.println("Writing out summary...");
					// Write summary to file
					final ObjectOutputStream objectOutputStream = new ObjectOutputStream(Files.newOutputStream(sumPath, StandardOpenOption.CREATE_NEW));
					objectOutputStream.writeObject(summary);
					objectOutputStream.flush();
					objectOutputStream.close();
					System.out.println("\nDone!\n");
					// Report operation time
					System.out.println(timeFromMillis(System.currentTimeMillis() - startTime));
				} else
					cancel();
			}
		} catch (ParseException e)
		{
			// Simply print exception message
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (NoSuchFileException e)
		{
			// Report that a file can't be found
			System.err.println("Unable to find file: " + e.getMessage());
			System.exit(2);
		} catch (Exception e)
		{
			// Any other exception
			System.err.println("Unable to complete execution, caught exception: [" + e + ']');
			e.printStackTrace();
			System.exit(10);
		}
	}
	
	/**
	 * Calculates the total amount of chunks to be produced.
	 * @param totalSize The size of the source file.
	 * @param chunkSize The size of the chunks.
	 * @return The amount of chunks calculated to be produced.
	 */
	public static int chunkAmount(long totalSize, long chunkSize)
	{
		return (int) Math.ceil((double) totalSize / chunkSize);
	}
	
	/**
	 * Calculate any remainder bytes for the last chunk.
	 * @param totalSize The size of the source file.
	 * @param chunkSize The size of the chunks.
	 * @return The file size of the final chunk.
	 */
	public static long remainderBytes(long totalSize, long chunkSize)
	{
		return totalSize - ((chunkAmount(totalSize, chunkSize) - 1) * chunkSize);
	}
	
	/**
	 * Converts a byte array into a hex string.
	 * @param bytes The byte array to convert.
	 * @return A hex string representing the array.
	 */
	public static String bytesToHex(byte[] bytes)
	{
		final StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
		{
			final String hex = Integer.toHexString(b & 0xff);
			if (hex.length() == 1)
				builder.append('0');
			builder.append(hex);
		}
		return builder.toString();
	}
	
	/**
	 * Convert a file size scale suffix to the practical representing number. 
	 * @param name The suffix.
	 * @return The magnitude the suffix represents, falls back to KiB (kibibytes).
	 */
	public static int magnitudeFromName(String name)
	{
		switch (name.toUpperCase())
		{
			case "GB": return GB;// 1024^3
			case "MB": return MB;// 1024^3
			case "KB":
			default: return KB;
		}
	}
	
	/**
	 * Constructs a readable string for the amount of time an operation took.
	 * @param timeIn The time in milliseconds.
	 * @return The readable string.
	 */
	public static String timeFromMillis(long timeIn)
	{
		final StringBuilder builder = new StringBuilder("Operation took: ");
		final long secondMillis = 1000, minuteMillis = 60 * secondMillis, hourMillis = 60 * minuteMillis;
		long time = timeIn;
		if (time >= hourMillis)
		{
			builder.append(Math.floorDiv(time, hourMillis)).append(" hour(s) ");
			time %= hourMillis;
		}
		if (time >= minuteMillis)
		{
			builder.append(Math.floorDiv(time, minuteMillis)).append(" minute(s) ");
			time %= minuteMillis;
		}
		builder.append((double) time / secondMillis).append(" second(s)");
		return builder.append('.').toString();
	}
	
	/**
	 * Estimates rounded file sizes.
	 * @param bytes The size in binary bytes.
	 * @return The rounded string.
	 */
	public static String roundBin(long bytes)
	{
		if (bytes >= GB)
			return String.valueOf(bytes / GB) + " GB";
		else if (bytes >= MB)
			return String.valueOf(bytes / MB) + " MB";
		else if (bytes >= KB)
			return String.valueOf(bytes / KB) + " KB";
		else
			return String.valueOf(bytes) + " B";
	}
	
	/**
	 * Cancels the whole operation.
	 */
	static void cancel()
	{
		System.out.println("Cancelling execution...");
		System.exit(0);
	}

	/**
	 * Supplies the checksum digest for file verification.
	 * @return A SHA-256 digest.
	 */
	static MessageDigest digestSupplier()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e)
		{
			System.err.println(e);
			System.exit(-1);
			return null;
		}
	}
	
}
