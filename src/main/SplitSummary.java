package main;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class to assist in merging by verifying chunk and full file checksums.
 * @author UFFR
 *
 */
public class SplitSummary implements Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 98569882255195051L;
	
	private transient Map<Path, byte[]> checksumMap = new HashMap<Path, byte[]>();
	
	private final long totalSize, chunkSize;
	private final String filename;
	private final int chunkAmount;
	private byte[] totalFileChecksum = new byte[0];
	/**
	 * Construct a new summary.
	 * @param totalSize Size of the source file.
	 * @param chunkSize Size of the chunks.
	 * @param filename Name of the source file.
	 */
	public SplitSummary(long totalSize, long chunkSize, String filename)
	{
		this.totalSize = totalSize;
		this.chunkSize = chunkSize;
		this.filename = filename;
		
		chunkAmount = Main.chunkAmount(totalSize, chunkSize);
	}
	
	private void writeObject(ObjectOutputStream outputStream) throws IOException
	{
		outputStream.defaultWriteObject();
		outputStream.writeObject(checksumMap.keySet().stream().collect(Collectors.toMap(SplitSummary::pathToArray, checksumMap::get)));
	}
	
	private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException
	{
		inputStream.defaultReadObject();
		final Map<String[], byte[]> savedMap = ((Map<String[], byte[]>) inputStream.readObject());
		checksumMap = new HashMap<Path, byte[]>(savedMap.keySet().stream().collect(Collectors.toMap(SplitSummary::pathGetter, savedMap::get)));
	}
	
	/**
	 * Checks a summary for any errors.
	 * @throws IllegalStateException If any error is detected.
	 */
	public void checkForErrors() throws Exception
	{
		if (totalSize <= 0)
			throw new IllegalStateException("Source file size noted as negative or zero, this should not be possible!");
		if (chunkSize <= 0)
			throw new IllegalStateException("Chunk size noted as negative or zero, this should not be possible!");
		if (chunkAmount != (int) Math.ceil((double) totalSize / chunkSize))
			throw new IllegalStateException("Chunk amount does not match calculated expectation, this should not be possible!");
		
		for (Path path : checksumMap.keySet())
		{
			if (!path.getFileName().toString().startsWith(filename) || !path.getFileName().toString().endsWith(".part"))
				throw new IllegalStateException("Registry contains paths with non-conforming naming conventions, this should not be possible!");
			if (!Files.exists(path))
				throw new IllegalStateException("Registry contains missing paths!");
		}
		
		for (byte[] sum : checksumMap.values())
		{
			if (sum == null)
				throw new IllegalStateException("Registry contains one or more null checksums, this should not be possible!");
			else if (sum.length != 32)
				throw new IllegalStateException("Registry contains unconventional checksum(s) of bit length, this should not be possible!");
		}
	}
	
	public long getTotalSize()
	{
		return totalSize;
	}
	
	public long getChunkSize()
	{
		return chunkSize;
	}
	
	public int getChunkAmount()
	{
		return chunkAmount;
	}
	
	/**
	 * Add a chunk to the registry.
	 * @param path The relative path to add to the registry.
	 * @param checksum The checksum of the chunk.
	 */
	public void addChunkPath(Path path, byte[] checksum)
	{
		checksumMap.put(path, checksum);
	}
	
	/**
	 * Retrieve a chunk's checksum.
	 * @param path The chunk's relative path.
	 * @return The checksum, if exists.
	 */
	public byte[] getChecksum(Path path)
	{
		return checksumMap.get(path);
	}
	
	/**
	 * Retrieve a chunk's checksum in hexadecimal.
	 * @param path The chunk's relative path.
	 * @return The checksum in hexadecimal, if exists.
	 */
	public String getHexChunksum(Path path)
	{
		return Main.bytesToHex(getChecksum(path));
	}
	
	/**
	 * Checks if the path exists in the registry.
	 * @param path The path to test.
	 * @return True if it exists, false if not.
	 */
	public boolean hasPath(Path path)
	{
		return checksumMap.containsKey(path);
	}
	
	/**
	 * Gets the source file's checksum.
	 * @return The raw byte checksum.
	 */
	public byte[] getTotalFileChecksum()
	{
		return totalFileChecksum.clone();
	}
	
	/**
	 * Gets the source file's checksum in hexadecimal.
	 * @return The hexadecimal checksum.
	 */
	public String getTotalFileChecksumHex()
	{
		return Main.bytesToHex(totalFileChecksum);
	}
	
	/**
	 * Sets the source file's checksum.
	 * @param totalFileChecksum The checksum to set, does not permit null.
	 */
	public void setTotalFileChecksum(byte[] totalFileChecksum)
	{
		this.totalFileChecksum = totalFileChecksum == null ? new byte[0] : totalFileChecksum;
	}
	
	/**
	 * The filename of the source file.
	 * @return String filename.
	 */
	public String getFilename()
	{
		return filename;
	}
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(totalFileChecksum);
		result = prime * result + Objects.hash(checksumMap, chunkAmount, chunkSize, filename, totalSize);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof SplitSummary))
			return false;
		final SplitSummary other = (SplitSummary) obj;
		return Objects.equals(checksumMap, other.checksumMap) && chunkAmount == other.chunkAmount
				&& chunkSize == other.chunkSize && Objects.equals(filename, other.filename)
				&& Arrays.equals(totalFileChecksum, other.totalFileChecksum) && totalSize == other.totalSize;
	}

	/**
	 * Converts all path elements to a string array for serialization.
	 * @param path The path to convert.
	 * @return A string array representing the path.
	 */
	private static String[] pathToArray(Path path)
	{
		final String[] names = new String[path.getNameCount()];
		for (int i = 0; i < path.getNameCount(); i++)
			names[i] = path.getName(i).toString();
		return names;
	}
	
	/**
	 * Converts a string array to a path for deserialization.
	 * @param path The string array representing a path.
	 * @return The path represented by the array.
	 */
	private static Path pathGetter(String[] path)
	{
		return path.length > 1 ? Paths.get(path[0], Arrays.copyOfRange(path, 1, path.length)) : Paths.get(path[0]);
	}
	
}
