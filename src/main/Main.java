package main;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

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
	private static final Options OPTIONS = new Options();
	private static final CommandLineParser PARSER = new DefaultParser();
	private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
	private static final Supplier<MessageDigest> DIGEST_SUPPLIER = () ->
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
	};
	private static final MessageDigest OUT_DIGEST = DIGEST_SUPPLIER.get();
	private static final MessageDigest IN_DIGEST = DIGEST_SUPPLIER.get();
	static final short KB = 1024;
	static final int MB = KB * 1024, GB = MB * 1024, MAX_BUFFER = MB * 20;
	static
	{
		final OptionGroup group = new OptionGroup();
		group.addOption(Option.builder("h").longOpt("help").desc("Print the help screen.").build());
		group.addOption(Option.builder("p").longOpt("path").desc("Path of the file to split or the checksum registry.").required().hasArg().argName("file path").optionalArg(false).build());
		OPTIONS.addOption(Option.builder("e").longOpt("export").desc("Path to export the split or merged file(s). Defaults to the input path.").hasArg().argName("export path").optionalArg(false).build());
		OPTIONS.addOption(Option.builder("s").longOpt("size").desc("Size of each chunk and its unit (KB - GB). Max size is the 2^31-1 and defaults to 10 MB.").valueSeparator(':').hasArgs().argName("size:unit").numberOfArgs(2).optionalArg(false).build());
		OPTIONS.addOption(Option.builder("m").longOpt("merge").desc("Merge chunks instead of splitting into them.").build());
		OPTIONS.addOptionGroup(group);
	}
	private static Path inputPath, outputPath;
	public static void main(String[] args)
	{
		final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		final long startTime;
		try
		{
			final CommandLine commandLine = PARSER.parse(OPTIONS, args);
			if (commandLine.hasOption('h'))
			{
				HELP_FORMATTER.printHelp("Requires specified file path.", "FileSplitter v0.5", OPTIONS, "Report bugs to the GitHub at: https://github.com/UFFR/FileSplitter", true);
				System.exit(0);
			}
			inputPath = Paths.get(commandLine.getOptionValue('p'));
			outputPath = Paths.get(commandLine.getOptionValue('e', inputPath.getParent().toString()));
			if (commandLine.hasOption('m'))
			{
				System.out.println("Recognized input path as " + inputPath + ", output path as " + outputPath + ". Continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					System.out.println("Beginning operation...");
					startTime = System.currentTimeMillis();
					final ObjectInputStream objectInputStream = new ObjectInputStream(Files.newInputStream(inputPath));
					final String outputFilename = (String) objectInputStream.readObject();
					final long totalSize = objectInputStream.readLong(), chunkSize = objectInputStream.readLong();
					final int listedChunks = objectInputStream.readInt(), calculatedChunks = (int) Math.round(Math.ceil((double) totalSize / (double) chunkSize));
					System.out.println("Output file will be " + outputFilename);
					System.out.println("Reported total file size is: " + totalSize);
					System.out.println("Reported chunk size is: " + chunkSize);
					System.out.println("Thus there should be " + calculatedChunks + " chunks.");
					if (calculatedChunks != listedChunks)
						System.err.println("WARNING! Expected " + calculatedChunks + " chunks but got " + listedChunks + " listed chunks!");
					try (final OutputStream outputStream = new DigestOutputStream(Files.newOutputStream(Paths.get(outputPath.toString(), outputFilename)), OUT_DIGEST))
					{
						for (int i = 1; i < listedChunks + 1; i++)
						{
							System.out.println("Reading chunk #" + i + '/' + listedChunks + "...");
							final Path currentPath = Paths.get(inputPath.getParent().toString(), outputFilename + '.' + i + ".part");
							final long currentChunkSize = Files.size(currentPath), desiredSize = i < listedChunks + 1 ? chunkSize : totalSize - chunkSize * i;
							if (desiredSize != chunkSize)
							{
								System.err.println("WARNING! Chunk #" + i + " has incorrect size! Expected " + desiredSize + " but got " + currentChunkSize + '!');
								System.out.println("Continue? (boolean)");
								if (!Boolean.parseBoolean(reader.readLine()))
									System.exit(0);
							}
							try (final InputStream inputStream = new DigestInputStream(Files.newInputStream(currentPath), IN_DIGEST))
							{
								long size = Files.size(currentPath);
								while (size > 0)
								{
									final int bufferSize = (int) Math.min(MAX_BUFFER, size);
									final byte[] buffer = new byte[bufferSize];
									inputStream.read(buffer);
									outputStream.write(buffer);
									size -= bufferSize;
								}
								outputStream.flush();
							}
							final String realChecksum = bytesToHex(IN_DIGEST.digest()), savedChecksum = (String) objectInputStream.readObject();
							if (!realChecksum.equals(savedChecksum))
							{
								System.err.println("WARNING! Chunk #" + i + "'s checksum does not match! Expected " + realChecksum + " but got " + savedChecksum + '!');
								System.out.println("Continue? (boolean)");
								if (!Boolean.parseBoolean(reader.readLine()))
									System.exit(0);
							}
						}
						outputStream.flush();
						final String realChecksum = bytesToHex(OUT_DIGEST.digest()), expectedChecksum = (String) objectInputStream.readObject();
						if (!realChecksum.equals(expectedChecksum))
							System.err.println("WARNING! Final file checksum does not match! Expected " + expectedChecksum + " but got " + realChecksum + '!');
					}
					System.out.println("Done!");
					System.out.println(timeFromMillis(System.currentTimeMillis() - startTime));
				}
				else
					cancel();
			}
			else
			{
				final String[] size = commandLine.hasOption('s') ? commandLine.getOptionValues('s') : new String[] {"10", "MB"};
				final long chunkSize = Long.parseLong(size[0]) * magnitudeFromName(size[1]), totalSize = Files.size(inputPath);
				final int totalChunks = (int) Math.round(Math.ceil((double) totalSize / (double) chunkSize));
				if (chunkSize < 0)
				{
					System.err.println("Invalid chunk size inputted!");
					System.exit(1);
				}
				System.out.println("Recognized input path as " + inputPath + ", output path as " + outputPath + ", chunk size as " + chunkSize + " bytes, making " + totalChunks + " total chunks. Continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					System.out.println("Beginning operation...");
					startTime = System.currentTimeMillis();
					final ObjectOutputStream objectOutputStream = new ObjectOutputStream(Files.newOutputStream(Paths.get(outputPath.toString(), inputPath.getFileName() + ".sum")));
					long processed = 0;
					objectOutputStream.writeObject(inputPath.getFileName().toString());
					objectOutputStream.writeLong(totalSize);
					objectOutputStream.writeLong(chunkSize);
					objectOutputStream.writeInt(totalChunks);
					try (final InputStream inputStream = new DigestInputStream(Files.newInputStream(inputPath), IN_DIGEST))
					{
						int chunkIndex = 1;
						while (inputStream.available() > 0)
						{
							final Path currentChunkPath = Paths.get(outputPath.toString(), inputPath.getFileName().toString() + '.' + chunkIndex + ".part");
							try (final OutputStream outputStream = new DigestOutputStream(Files.newOutputStream(currentChunkPath), OUT_DIGEST))
							{
								final long maxAlloc = Math.min(chunkSize, totalSize - processed);
								long written = 0;
								while (written < maxAlloc)
								{
									final int bufferSize = (int) Math.min(MAX_BUFFER, maxAlloc);
									final byte[] buffer = new byte[bufferSize];
									inputStream.read(buffer);
									outputStream.write(buffer);
									written += bufferSize;
								}

								outputStream.flush();
								processed += maxAlloc;
								objectOutputStream.writeObject(bytesToHex(IN_DIGEST.digest()));
								objectOutputStream.flush();
								System.out.println(String.format("(#%s/%s) Wrote: %s at %s bytes.", chunkIndex, totalChunks, currentChunkPath, maxAlloc));
							}
							chunkIndex++;
						}
						objectOutputStream.writeObject(bytesToHex(OUT_DIGEST.digest()));
						objectOutputStream.flush();
					}
					System.out.println("Done!");
					System.out.println(timeFromMillis(System.currentTimeMillis() - startTime));
				}
				else
					cancel();
			}
		} catch (ParseException e)
		{
			System.err.println(e);
			System.exit(1);
		} catch (NoSuchFileException e)
		{
			System.err.println("Unable to find file: " + e.getMessage());
			System.exit(2);
		} catch (Exception e)
		{
			System.err.println("Unable to complete execution, caught exception: " + e + '.');
			e.printStackTrace();
			System.exit(10);
		}
	}
	
	private static String bytesToHex(byte[] bytes)
	{
		final StringBuilder builder = new StringBuilder();
		for (byte b : bytes)
		{
			final String hex = Integer.toHexString(b & 0xff);
			if (hex.length() == 1)
				builder.append('0');
			builder.append(hex);
		}
		return builder.toString();
	}
	
	private static int magnitudeFromName(String name)
	{
		switch (name.toUpperCase())
		{
			case "GB": return GB;// 1024^3
			case "MB": return MB;// 1024^3
			case "KB":
			default: return KB;
		}
	}
	
	private static String timeFromMillis(long timeIn)
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
		if (time >= secondMillis)
			builder.append((double) time / secondMillis).append(" second(s)");
		return builder.append('.').toString();
	}
	
	private static void cancel()
	{
		System.out.println("Cancelling execution...");
		System.exit(0);
	}

}
