package eu.openaire.doc_urls_retriever.util.url;

import eu.openaire.doc_urls_retriever.DocUrlsRetriever;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;


/**
 * This class is responsible to store the quadruple <urlId, sourceUrl, docUrl, errorCause> for it to be written in the outputFile.
 * @author Lampros Smyrnaios
 */
public class DataToBeLogged
{
    private String urlId;
    private String sourceUrl;
    private String docUrl;
	String wasUrlChecked, wasUrlValid, wasDocumentOrDatasetAccessible, wasDirectLink;
    private String comment;   // This will be an emptyString, unless there is an error causing the docUrl to be unreachable.
	
	private static final Logger logger = LoggerFactory.getLogger(DataToBeLogged.class);

	public DataToBeLogged(String urlId, String sourceUrl, String docUrl, String wasUrlChecked, String wasUrlValid, String wasDocumentOrDatasetAccessible, String wasDirectLink, String comment)
    {
        if ( urlId == null )
            urlId = "unretrievable";
        
        this.urlId = urlId;
        this.sourceUrl = escapeSourceUrl(sourceUrl);	// The input may have non-expected '\"', '\\' or even '\\\"' which will be unescaped by JsonObject and we have to re-escape them in the output.
        this.docUrl = docUrl;
		this.wasUrlChecked = wasUrlChecked;
		this.wasUrlValid = wasUrlValid;
		this.wasDocumentOrDatasetAccessible = wasDocumentOrDatasetAccessible;
		this.wasDirectLink = wasDirectLink;
		this.comment = comment;
	}
	
	
	/**
	 * This method, escapes the <backSlashes> and the <doubleQuotes> from the sourceUrl.
	 * When we read from jsonObjects, the string returns unescaped.
	 * Now, there are libraries for escaping and unescaping chars, like "org.apache.commons.text.StringEscapeUtils".
	 * But they can't handle the case where you want this: \"   to be this: \\\"   as they thing you are already satisfied what what you have.
	 * Tha might be true in general.. just not when you want to have a valid-jason-output.
	 * @param sourceUrl
	 * @return
	 */
	public static String escapeSourceUrl(String sourceUrl)
	{
		/*
			Here we might even have these in the input  <\\\"> which will be read by jsonObject as <\"> and we will have to re-make them <\\\"> in order to have a valid-json-output.
			http://www.scopus.com/record/display.url?eid=2-s2.0-82955208478&origin=resultslist&sort=plf-f&src=s&st1=aZZONI+r&nlo=&nlr=&nls=&sid=YfPXTZ5QQuqvNMHCo-geSvN%3a60&sot=b&sdt=cl&cluster=scoauthid%2c%227004337609%22%2ct%2bscosubtype%2c%22ar%22%2ct%2bscosubjabbr%2c%22MEDI%22%2ct%2c%22MULT%22%2ct&sl=21&s=AUTHOR-NAME%28aZZONI+r%29&relpos=0&relpos=0&searchTerm=AUTHOR-NAME(aZZONI r) AND ( LIMIT-TO(AU-ID,\\\"Azzoni, Roberto\\\" 7004337609) ) AND ( LIMIT-TO(DOCTYPE,\\\"ar\\\" ) ) AND ( LIMIT-TO(SUBJAREA,\\\"MEDI\\\" ) OR LIMIT-TO(SUBJAREA,\\\"MULT\\\" ) )
		 */
		
		// Escape backSlash.
		sourceUrl = StringUtils.replace(sourceUrl, "\\", "\\\\", -1);	// http://koara.lib.keio.ac.jp/xoonips/modules/xoonips/detail.php?koara_id=pdf\AN00150430-00000039--001
		
		// Escape doubleQuotes and return.
		return StringUtils.replace(sourceUrl, "\"", "\\\"", -1);	// https://jual.nipissingu.ca/wp-content/uploads/sites/25/2016/03/v10202.pdf" rel="
	}
    
    
    /**
     * This method returns this object in a valid jsonString. It does not need to be synchronized.
     * @return jsonString
     */
    public String toJsonString()
    {
		JSONObject jsonObject = new JSONObject();
		try {	//	Change the underlying structure to a "LinkedHashMap", in order to avoid unordered-representation.
			Field changeMap = jsonObject.getClass().getDeclaredField("map");
			changeMap.setAccessible(true);
			changeMap.set(jsonObject, new LinkedHashMap<>());
			changeMap.setAccessible(false);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			logger.warn("Could not create an ordered JSONObject, so continuing with an unordered one. Exception msg: " + e.getMessage());
			jsonObject = new JSONObject();	// In this case, just create a new "normal" JSONObject.
		}

		try {
			if ( LoaderAndChecker.useIdUrlPairs ) {
				jsonObject.put("id", this.urlId);
			}
			jsonObject.put("sourceUrl", this.sourceUrl);
			jsonObject.put(DocUrlsRetriever.targetUrlType, this.docUrl);
			jsonObject.put("wasUrlChecked", this.wasUrlChecked);
			jsonObject.put("wasUrlValid", this.wasUrlValid);
			jsonObject.put("wasDocumentOrDatasetAccessible", this.wasDocumentOrDatasetAccessible);
			jsonObject.put("wasDirectLink", this.wasDirectLink);
			jsonObject.put("comment", this.comment);
		} catch (JSONException je) {
			// Keep the jsonObject with what it has till now.. plus a "special" comment.
			jsonObject.put("comment", "There was a problem creating this JSON with the right values.");
			logger.warn("Invalid JsonOutput will be written as: " + jsonObject);	// Depending on which json-key is the pre-last one, we will know what caused the error.
		}

        return jsonObject.toString();
    }
    
}
