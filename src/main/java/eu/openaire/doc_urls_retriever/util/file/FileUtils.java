package eu.openaire.doc_urls_retriever.util.file;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.openaire.doc_urls_retriever.exceptions.DocFileNotRetrievedException;
import eu.openaire.doc_urls_retriever.util.url.TripleToBeLogged;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.io.FileUtils.deleteDirectory;


/**
 * @author Lampros A. Smyrnaios
 */
public class FileUtils
{
	private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
	
	private static Scanner inputScanner;
	private static PrintStream printStream;
	//public static HashMap<String, String> idAndUrlMappedInput = new HashMap<String, String>();	// Contains the mapped key(id)-value(url) pairs.
	private static long fileIndex = 0;	// Index in the input file
	public static boolean skipFirstRow = true;
	private static String endOfLine = "\n";
	public static long unretrievableInputLines = 0;	// For better statistics in the end.
    public static long unretrievableUrlsOnly = 0;
    public static int groupCount = 5000;	// Just for testing.. TODO -> Later increase it..
	
	public static List<TripleToBeLogged> tripleToBeLoggedOutputList = new ArrayList<>();
	
	public static final HashMap<String, Integer> numbersOfDuplicateDocFileNames = new HashMap<String, Integer>();	// Holds docFileNa,es with their duplicatesNum.
	
	public static final boolean shouldDownloadDocFiles = true;
	public static final boolean shouldDeleteOlderDocFiles = true;	// Should we delete any older stored docFiles? This is useful for testing.
	public static String docFilesDownloadPath = "//media//lampros//HDD2GB//downloadedDocFiles";
	public static long unretrievableDocNamesNum = 0;	// Num of docFiles for which we were not able to retrieve their docName.
	public static final Pattern FILENAME_FROM_CONTENT_DISPOSITION_FILTER = Pattern.compile(".*(?:filename=(?:\\\")?)([\\w\\-\\.\\%\\_]+)[\\\"\\;]*.*");
	
	
	public FileUtils(InputStream input, OutputStream output)
	{
    	logger.debug("Input: " + input.toString());
    	logger.debug("Output: " + output.toString());
    	
		FileUtils.inputScanner = new Scanner(input);
		FileUtils.printStream = new PrintStream(output);
		
		if ( shouldDownloadDocFiles ) {
			if ( shouldDeleteOlderDocFiles ) {
				try {
					logger.debug("Deleting old docFiles..");
					File dir = new File(docFilesDownloadPath);
					deleteDirectory(dir);
					if ( !dir.exists() )
						if ( !dir.mkdir() )    // Create the directory.
							logger.warn("Problem when creating the dir: " + docFilesDownloadPath);
				} catch (Exception e) {
					logger.warn("Problem when deleting directory: " + docFilesDownloadPath, e);
					
				}
			}
		}
	}
	
	
	/**
	 * This method returns the number of (non-heading, non-empty) lines we have read from the inputFile.
	 * @return loadedUrls
	 */
	public static long getCurrentlyLoadedUrls()	// In the end, it gives the total number of urls we have processed.
	{
		if ( FileUtils.skipFirstRow )
			return FileUtils.fileIndex - FileUtils.unretrievableInputLines - FileUtils.unretrievableUrlsOnly -1; // -1 to exclude the first line
		else
			return FileUtils.fileIndex - FileUtils.unretrievableInputLines - FileUtils.unretrievableUrlsOnly;
	}


	/**
	 * This method parses a Json file and extracts the urls, along with the IDs.
	 * @return Collection<String>
	 */
	public static Collection<String> getNextUrlGroupFromJson()
	{
		skipFirstRow = false;	// Make sure we don't use this rule for any calculations.
		
		HashMap<String, String> inputIdUrlPair;
		Collection<String> urlGroup = new HashSet<String>();
		
		long curBeginning = FileUtils.fileIndex;
		
		while ( (inputScanner.hasNextLine()) && (FileUtils.fileIndex < (curBeginning + groupCount)) )// While (!EOF) iterate through lines.
		{
			//logger.debug("fileIndex: " + FileUtils.fileIndex);	// DEBUG!
			
			// Take each line, remove potential double quotes.
			String retrievedLineStr = inputScanner.nextLine();
			//logger.debug("Loaded from inputFile: " + retrievedLineStr);	// DEBUG!
			
			FileUtils.fileIndex ++;
			
			if (retrievedLineStr.isEmpty()) {
				FileUtils.unretrievableInputLines ++;
				continue;
			}
			
			inputIdUrlPair = jsonDecoder(retrievedLineStr); // Decode the jsonLine and take the two attributes.
			if ( inputIdUrlPair == null ) {
				logger.warn("A problematic inputLine found: \"" + retrievedLineStr + "\"");
				FileUtils.unretrievableInputLines ++;
				continue;
			}
			
			// TODO - Find a way to keep track of the redirections over the input pages, in order to match the id of the original-input-docPage, with the redirected-final-docPage.
			// So that the docUrl in the output will match to the ID of the inputDocPage.
			// Currently there is no control over the correlation of pre-redirected pages and after-redirected ones, as Crawler4j, which handles this process doesn't keep track of such thing.
			// So the output currently contains the redirected-final-docPages with their docUrls.
			
			//idAndUrlMappedInput.putAll(inputIdUrlPair);    // Keep mapping to be put in the outputFile later.
			
			urlGroup.addAll(inputIdUrlPair.values());	// Make sure that our returning's source is the temporary collection (otherwise we go into an infinite loop).
		}
		
		return urlGroup;	// Return just the urls to be crawled. We still keep the IDs.
	}


	/**
	 * This method decodes a Jason String into its members.
	 * @param jsonLine String
	 * @return HashMap<String,String>
	 */
	public static HashMap<String,String> jsonDecoder(String jsonLine)
	{
		HashMap<String, String> returnIdUrlMap = new HashMap<String, String>();

		JSONObject jObj = new JSONObject(jsonLine); // Construct a JSONObject from the retrieved jsonLine.

		// Get ID and url and put them in the HashMap
		String idStr = null;
		String urlStr = null;
		try {
			idStr = jObj.get("id").toString();
			urlStr = jObj.get("url").toString();
		} catch (JSONException je) {
			logger.warn("JSONException caught when tried to retrieve values from jsonLine: \"" + jsonLine + "\"", je);
			return null;
		}

		if ( idStr.isEmpty() && urlStr.isEmpty() )	// Allow one of them to be empty but not both. If ID is empty, then we still don't lose the URL.
			return null;	// If url is empty, we will still see the ID in the output and possible find its missing URL later.
        else if ( urlStr.isEmpty() )    // Keep track of lines with an id, but, with no url.
            FileUtils.unretrievableUrlsOnly++;

		returnIdUrlMap.put(idStr, urlStr);

		return returnIdUrlMap;
	}


	/**
	 * This method encodes json members into a Json object and returns its String representation..
	 * @param sourceUrl String
	 * @param docUrl String
	 * @param errorCause String
	 * @return jsonString
	 */
	public static String jsonEncoder(String sourceUrl, String docUrl, String errorCause)
	{
		JSONArray jsonArray;
		try {
			JSONObject firstJsonObject = new JSONObject().put("sourceUrl", sourceUrl);
			JSONObject secondJsonObject = new JSONObject().put("docUrl", docUrl);
			JSONObject thirdJsonObject = new JSONObject().put("errorCause", errorCause);	// The errorCause will be empty, if there is no error.

			// Care about the order of the elements by using a JSONArray (otherwise it's uncertain which one will be where).
			jsonArray = new JSONArray().put(firstJsonObject).put(secondJsonObject).put(thirdJsonObject);
		} catch (Exception e) {	// If there was an encoding problem.
			logger.error("Failed to encode jsonLine!", e);
			return null;
		}

		return jsonArray.toString();	// Return the jsonLine.
	}
	
	
	/**
	 * This function writes new source-doc URL set in the output file.
	 * Each time it's finished writing, it flushes the write stream and clears the urlTable.
	 */
	public static void writeToFile()
	{
		int numberOfTriples = FileUtils.tripleToBeLoggedOutputList.size();
		StringBuilder strB = new StringBuilder(numberOfTriples * 350);  // 350: the maximum expected length for a source-doc-error triple..

		String tempJsonString = null;

		for ( TripleToBeLogged triple : FileUtils.tripleToBeLoggedOutputList)
		{
            tempJsonString = triple.toJsonString();
			if ( tempJsonString == null )	// If there was an encoding error, move on..
				continue;
			
			strB.append(tempJsonString);
			strB.append(endOfLine);
		}
		
		printStream.print(strB.toString());
		printStream.flush();
		
		FileUtils.tripleToBeLoggedOutputList.clear();	// Clear to keep in memory only <groupCount> values at a time.
		
		logger.debug("Finished writing to the outputFile.. " + numberOfTriples + " set(s) of (\"SourceUrl\", \"DocUrl\")");
	}
	
	
	/**
	 * This method is responsible for storing the docFiles and store them in permanent storage.
	 * @param contentData
	 * @param docUrl
	 * @param contentDisposition
	 * @throws DocFileNotRetrievedException
	 */
	public static void storeDocFile(byte[] contentData, String docUrl, String contentDisposition) throws DocFileNotRetrievedException
	{
		// TODO - Maybe it would be helpful to make it return the docFile's name for this to be included in the JSONoutput list.
		
		if ( contentData.length == 0 )
			throw new DocFileNotRetrievedException();
		
		String docFileName = null;
		boolean hasUnretrievableDocName = false;
		
		if ( contentDisposition != null ) {	// Extract docFileName from contentDisposition.
			Matcher fileNameMatcher = FILENAME_FROM_CONTENT_DISPOSITION_FILTER.matcher(contentDisposition);
			if ( fileNameMatcher.matches() ) {
				docFileName = fileNameMatcher.group(1);	// Group<1> is the fileName.
				if ( docFileName == null || docFileName.isEmpty() )
					hasUnretrievableDocName = true;
			}
			else {
				logger.warn("Unmatched Content-Disposition:  " + contentDisposition);
				hasUnretrievableDocName = true;
			}
		}
		else { // Extract fileName from docUrl.
			docFileName = UrlUtils.getDocIdStr(docUrl);
			if ( docFileName == null )
				hasUnretrievableDocName = true;
		}
		
		if ( hasUnretrievableDocName )
			docFileName = "unretrievableDocName(" + (++unretrievableDocNamesNum) + ").pdf";	// TODO - Later, when having more fileTypes, maybe MAP mimeTypes with fileExtentions
		
		try {
			String saveDocFileFullPath = docFilesDownloadPath + File.separator + docFileName;
			
			// Open an outputStream to save the docFile.
			File docFile = new File(saveDocFileFullPath);
			if ( docFile.exists() ) {
				// Count duplicates.
				int curDuplicateNum = 1;
				if ( numbersOfDuplicateDocFileNames.containsKey(docFileName) )
					curDuplicateNum += numbersOfDuplicateDocFileNames.get(docFileName);
				numbersOfDuplicateDocFileNames.put(docFileName, curDuplicateNum);
				
				// Construct final-DocFileName.
				String preExtensionFileName = docFileName.substring(0, docFileName.lastIndexOf(".") -1);
				String fileExtension = docFileName.substring(docFileName.lastIndexOf("."));
				String newEndingName = preExtensionFileName + "(" + curDuplicateNum + ")" + fileExtension;
				saveDocFileFullPath = docFilesDownloadPath + File.separator + newEndingName;
				File renamedDocFile = new File(saveDocFileFullPath);
				if ( !docFile.renameTo(renamedDocFile) ) {
					logger.error("Renaming operation for \"" + docFileName + "\" has failed!");
					throw new DocFileNotRetrievedException();
				}
			}
			
			FileOutputStream outputStream = new FileOutputStream(docFile);
			
			outputStream.write(contentData, 0, contentData.length - 1);
			outputStream.close();
			
			logger.debug("DocFile: \"" + docFileName + "\" seems to have been downloaded! Go check it out!");	// DEBUG!
			
		} catch (Exception ioe) {
			logger.warn("", ioe);
			throw new DocFileNotRetrievedException();
		}
	}
	
	
	/**
	 * Closes open Streams.
	 */
	public static void closeStreams()
	{
        inputScanner.close();
		printStream.close();
	}
	
	
	/**
	 * This method parses a testFile with one-url-per-line and extracts the urls.
	 * @return Collection<String>
	 */
	public static Collection<String> getNextUrlGroupTest()
	{
		Collection<String> urlGroup = new HashSet<String>();
		
		// Take a group of <groupCount> urls from the file..
		// If we are at the end and there are less than <groupCount>.. take as many as there are..
		
		//logger.debug("Retrieving the next group of " + groupCount + " elements from the inputFile.");
		long curBeginning = FileUtils.fileIndex;
		
		while ( (inputScanner.hasNextLine()) && (FileUtils.fileIndex < (curBeginning + groupCount)) )
		{// While (!EOF) iterate through lines.
			
			// Take each line, remove potential double quotes.
			String retrievedLineStr = StringUtils.replace(inputScanner.nextLine(), "\"", "");
			
			FileUtils.fileIndex ++;
			
			if ( (FileUtils.fileIndex == 1) && skipFirstRow )
				continue;
			
			if ( retrievedLineStr.isEmpty() ) {
				FileUtils.unretrievableInputLines ++;
				continue;
			}
			
			//logger.debug("Loaded from inputFile: " + retrievedLineStr);	// DEBUG!
			
			urlGroup.add(retrievedLineStr);
		}
		//logger.debug("FileUtils.fileIndex's value after taking urls after " + FileUtils.fileIndex / groupCount + " time(s), from input file: " + FileUtils.fileIndex);	// DEBUG!
		
		return urlGroup;
	}

}
