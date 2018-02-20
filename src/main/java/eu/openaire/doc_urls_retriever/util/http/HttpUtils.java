package eu.openaire.doc_urls_retriever.util.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.openaire.doc_urls_retriever.util.url.UrlUtils;



public class HttpUtils
{
	private static final Logger logger = LogManager.getLogger(HttpUtils.class);
	
	public static HashSet<String> domainsWithUnsupportedHeadMethod = new HashSet<String>();
	public static HashSet<String> blacklistedDomains = new HashSet<String>();	// Domains with which we don't want to connect again.
	public static HashMap<String, Integer> domainsReturned403 = new HashMap<String, Integer>();	// Domains that have returned 403 responce, and the amount of times they did.
	public static HashMap<String, Integer> domainsWithTimeoutEx = new HashMap<String, Integer>();
	

    public static String lastConnectedHost = "";
    public static int politenessDelay = 250;	// Time to wait before connecting to the same host again.
	public static int maxConnWaitingTime = 15000;	// Max time (in ms) to wait for a connection.
    private static int maxRedirects = 5;	// It's not worth waiting for more than 3, in general.. except if we turn out missing a lot of them.. test every case and decide.. 
    										// The usual redirect times for doi.org urls is 3, though some of them can reach even 5 (if not more..)
    private static int timesToReturn403BeforeBlocked = 10;	// Urls from specific domains are continuously returning 403 responceCode. Unless we find a way around it (handle connectivity differently ?), we should block the domains.
    private static int timesToHaveTimeoutExBeforeBlocked = 3;
    
    /**
     * This method sets up a connection with the given url, using the "HEAD" method. If the server doesn't support "HEAD", it logs it, then it resets the connection and tries again using "GET".
     * The "domainStr" may be either null, if the calling method doesn't know this String (then openHttpConnection() finds it on its own), or an actual "domainStr" String.
     * @param resourceURL
     * @param domainStr
     * @return HttpURLConnection
     * @throws RuntimeException
     */
	public static HttpURLConnection openHttpConnection(String resourceURL, String domainStr) throws RuntimeException
    {
    	URL url = null;
		HttpURLConnection conn = null;
		int responceCode = 0;

		try {
			if ( domainStr == null ) {	// No info about dominStr from the calling method "handleRedirects()", we have to find it here.
				if ( (domainStr = UrlUtils.getDomainStr(resourceURL)) == null )
					throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".
			}
			
			if ( blacklistedDomains.contains(domainStr) ) {
		    	logger.warn("Preventing connecting to blacklistedHost: \"" + domainStr + "\"!");
		    	throw new RuntimeException();
			}
			
			url = new URL(resourceURL);
			
			conn = (HttpURLConnection) url.openConnection();
			
			conn.setInstanceFollowRedirects(false);	// We manage redirects on our own, in order to control redirectsNum as well as to be able to handle single http to https redirect without having to do a network redirect.
			conn.setReadTimeout(maxConnWaitingTime);
			conn.setConnectTimeout(maxConnWaitingTime);
			
			if ( domainsWithUnsupportedHeadMethod.contains(domainStr) )	// If we know that it doesn't support "HEAD"..
				conn.setRequestMethod("GET");	// Go directly with "GET".
			else
				conn.setRequestMethod("HEAD");	// Else, try "HEAD" (it may be either a domain that supports "HEAD", or a new domain, for which we have no info yet).
			
			if ( domainStr.equals(lastConnectedHost) ) {	// If this is the last-visited domain, sleep a bit before re-connecting to it.
				Thread.sleep(politenessDelay);	// Avoid server-overloading for the same host.
				conn.connect();
			} else {
				conn.connect();	// Else, first connect and if there is no error, log this domain as the last one.
				lastConnectedHost = domainStr;
			}
			
			if ( (responceCode = conn.getResponseCode()) == -1 )
			{
				logger.warn("Invalid HTTP response for \"" + conn.getURL().toString() + "\"");
				throw new RuntimeException();
			}
			
			if ( responceCode == 405 || responceCode == 501 )	// If this SERVER doesn't support "HEAD" method or doesn't allow us to use it..
			{
				//logger.debug("HTTP \"HEAD\" method is not supported for: \"" + resourceURL +"\". Server's responceCode was: " + responceCode);
				
				// This domain doesn't support "HEAD" method, log it and retry connecting, using "GET" method this time.
				domainsWithUnsupportedHeadMethod.add(domainStr);
				
				conn.disconnect();
				conn = null;
				
				conn = (HttpURLConnection) url.openConnection();
				
				conn.setInstanceFollowRedirects(false);
				conn.setReadTimeout(maxConnWaitingTime);
				conn.setConnectTimeout(maxConnWaitingTime);
				
				conn.setRequestMethod("GET");
				
				Thread.sleep(politenessDelay);	// Avoid server-overloading for the same host.
				
				conn.connect();
				
				//logger.debug("ResponceCode for \"" + resourceURL + "\", after setting conn-method to: \"" + conn.getRequestMethod() + "\" is: " + conn.getResponseCode());
			}
		} catch (UnknownHostException uhe) {
	    	logger.debug("A new \"Unknown Network\" Host was found and logged: \"" + domainStr + "\"");
			blacklistedDomains.add(domainStr);	//Log it to never try connecting with it again.
			conn.disconnect();
			throw new RuntimeException();
		} catch (SocketTimeoutException ste) {
			logger.debug("Url: \"" + resourceURL + "\" failed to respond on time!");
			onTimeoutException(domainStr);
			conn.disconnect();
			throw new RuntimeException();
    	} catch (Exception e) {
    		if ( !(e instanceof RuntimeException) )	// If it's an instance then it's already logged.
    			logger.warn(e);
			if ( conn != null )
				conn.disconnect();
			throw new RuntimeException();
		}
		
		return conn;
    }
    
	
    /**
     * This method takes an open connection and determines if there is a need for redirections.
     * If there is, then it opens a new connection every time, up to the point we reach a certain number of redirections.
     * 
     * @param conn
     * @return Last open connection. If there was any problem, it returns "null".
     * @throws IOException
     * @throws MalformedURLException
     * @throws InterruptedException
     */
	public static HttpURLConnection handleRedirects(HttpURLConnection conn, int responceCode, String domainStr)
	{
		boolean redirect;
		int redirectsNum = 0;
		String initialUrl = conn.getURL().toString();	// Used to have the initialUrl to run tests on redirections (number, path etc..)
		
		try {
			do {
				redirect = false;
				
				// Check if there was a previous redirection in which we were redirected to a different domain.
				if ( domainStr == null ) {	// If this is the case, get the new dom
					if ( (domainStr = UrlUtils.getDomainStr(conn.getURL().toString())) == null )
						throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".
				}
				
				if ( responceCode >= 300 && responceCode <= 307 && responceCode != 306 && responceCode != 304 )	// Redirect code.
				{
					redirect = true;
					redirectsNum ++;
					
					if ( redirectsNum > HttpUtils.maxRedirects ) {
						logger.warn("Redirects exceeded their limit (" + HttpUtils.maxRedirects + ") for \"" + initialUrl + "\"");
						throw new RuntimeException();
					}
					
					String location = conn.getHeaderField("Location");
					if ( location == null ) {
						logger.warn("No \"Location\" field was found in the HTTP Header of \"" + conn.getURL().toString() + "\", after recieving an \"HTTP " + responceCode + "\" Redirect Code.");
						throw new RuntimeException();
					}
					else if ( location.contains("gateway/error") || location.contains("/error/") || location.contains("/sorryserver") ) {	// TODO - Investigate and add more error cases. 
						logger.warn("Url: \"" + initialUrl +  "\" was prevented to redirect to error page: \"" + location + "\", after recieving an \"HTTP " + responceCode + "\" Redirect Code.");
						throw new RuntimeException();
					}
					
					URL base = conn.getURL();
					URL target = new URL(base, location);
					
					String targetUrlStr = target.toString();
					
					// FOR DEBUG -> Check to see what's happening with the redirect urls (location field types, as well as potential error redirects).
					// Some domains use only the target-ending-path in their location field, while others use full target url.
					//if ( conn.getURL().toString().contains("plos.org") ) {	// Debug a certain domain.
						/*logger.debug("\n");
						logger.debug("Redirect(s) num: " + redirectsNum);
						logger.debug("Redirect code: " + conn.getResponseCode());
						logger.debug("Base: " + base.toString());
						logger.debug("Location: " + location);
						logger.debug("Target: " + targetUrlStr + "\n");*/
					//}
					
					if ( !targetUrlStr.contains(HttpUtils.lastConnectedHost) )	// If the next page is not in the same domain as the "lastConnectedHost", we have to find the domain again inside "openHttpConnection()" method.
						domainStr = null;
					
					conn.disconnect();
					
					conn = HttpUtils.openHttpConnection(targetUrlStr, domainStr);
					
					responceCode = conn.getResponseCode();	// It's already checked for -1 case (Invalid HTTP), inside openHttpConnection().
					if ( (responceCode >= 200) && (responceCode <= 299) )
                        return conn;	// It's an "HTTP SUCCESS", return immediately.
				}
				else if ( responceCode >= 500 ) {	// Server Error.
		        	logger.debug("Url: \""+ conn.getURL().toString() +"\" seems to be unreachable. Recieved: HTTP " + responceCode + " Server Error.");
		        	
		        	// Blacklist this domain..
		        	blacklistedDomains.add(conn.getURL().toString());
		        	
		        	throw new RuntimeException();
		        }
		        else if ( responceCode >= 400 ) {	// Client Error.
		        	logger.debug("Url: \""+ conn.getURL().toString() +"\" seems to be unreachable. Recieved: HTTP " + responceCode + " Client Error");
		        	
		        	// If a domain keeps returning 403 for different urls (they will be, as we don't allow duplicates) after a pre-defined number of times, we should block it.
		        	if ( responceCode == 403)
		        		on403ResponceCode(domainStr);
		        	
		        	throw new RuntimeException();
		        }
		        else {
		        	logger.warn("Unexpected responce code recieved: \"HTTP " + responceCode + "\", for url: \"" + conn.getURL().toString() + "\"" );
		        	throw new RuntimeException();
		        }
				
			} while (redirect);
			
		} catch (RuntimeException re) {	// We already logged the right messages.
			conn.disconnect();
			throw new RuntimeException();
		} catch (Exception e) {
			logger.warn(e);
			conn.disconnect();
			throw new RuntimeException();
		}
		
		
	// Here is a DEBUG section in which we can retrieve statistics about redirections of certain domains.
/*		if ( initialUrl.contains("www.ncbi.nlm.nih.gov") )	// DEBUG
			logger.info("\"" + initialUrl + "\" DID: " + redirectsNum + " redirect(s)!");	// DEBUG!
		
		if ( initialUrl.contains("doi.org/") )	// Check how many redirection are done for doi.org urls..
			if ( redirectsNum == maxRedirects ) {
				logger.info("DOI.ORG: \"" + initialUrl + "\" DID: " + redirectsNum + " redirect(s)!");	// DEBUG!
				logger.info("Final link is: \"" + conn.getURL().toString() + "\"");	// DEBUG!
			}
*/
		
		return conn;	// Returns the "conn" as it is (not disconnected), as it will be used by the calling function. 
	}
	
	
	public static void onTimeoutException(String domainStr)
	{
		if ( blockDomainTypeAfterTimes(domainsWithTimeoutEx, domainStr, timesToHaveTimeoutExBeforeBlocked) )
			logger.debug("Domain: \"" + domainStr + "\" was blocked after causing Timeout Exception " + timesToHaveTimeoutExBeforeBlocked + " times.");
	}
	
	
	public static void on403ResponceCode(String domainStr)
	{
		if ( blockDomainTypeAfterTimes(domainsReturned403, domainStr, timesToReturn403BeforeBlocked) )
			logger.debug("Domain: \"" + domainStr + "\" was blocked after returning HTTP 403 " + timesToReturn403BeforeBlocked + " times.");
	}


    /**
     * This method handles domains which are reaching cases were they can be blocked.
     * To be blocked they have to never have provided a docUrl.
     * @param domainsHashSet
     * @param domainStr
     * @param timesBeforeBlock
     * @return
     */
	public static boolean blockDomainTypeAfterTimes(HashMap<String, Integer> domainsHashSet, String domainStr, int timesBeforeBlock)
	{
        if ( !UrlUtils.successDomainPathsMultiMap.containsKey(domainStr) ) {	// If this domain hasn't give a docUrl yet
            int curTimes = 1;
            if ( domainsHashSet.containsKey(domainStr) )
                curTimes += domainsHashSet.get(domainStr).intValue();

            domainsHashSet.put(domainStr, curTimes);

            if ( curTimes > timesBeforeBlock ) {
                    blacklistedDomains.add(domainStr);	// Block this domain
                    return true;
            }
		}
		return false;
	}
	
}
