package eu.openaire.doc_urls_retriever.util.url;


import eu.openaire.doc_urls_retriever.util.http.ConnSupportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;


/**
 * This class contains methods which inspect the urls -based on REGEXES and specific strings- and decide whether they are wanted or not.
 * @author Lampros A. Smyrnaios
 */
public class UrlTypeChecker
{
	private static final Logger logger = LoggerFactory.getLogger(UrlTypeChecker.class);
	
	public static final Pattern URL_DIRECTORY_FILTER =
			Pattern.compile(".*\\/(?:profile|login|auth\\.|authentication\\.|ac(?:c)?ess|join|subscr|register|submit|post\\/|send\\/|shop\\/|watch|import|bookmark|announcement|rss|feed|about|faq|wiki|news|events|cart|support|sitemap|htmlmap|license|disclaimer|polic(?:y|ies)|privacy|terms|help|law"
					+ "|(?:my|your)?account|user|fund|aut(?:h)?or|editor|citation|review|external|statistics|application|permission|ethic|conta(?:c)?t|survey|wallet|contribute|deposit|donate|template|logo|image|photo|advertiser|people|(?:the)?press"
					+ "|error|(?:mis|ab)use|gateway|sorryserver|cookieabsent|notfound|404\\.(?:\\w)?htm).*");
	// We check them as a directory to avoid discarding publications's urls about these subjects. There's "acesso" (single "c") in Portuguese.. Also there's "autore" & "contatto" in Italian.
	
	public static final Pattern CURRENTLY_UNSUPPORTED_DOC_EXTENSION_FILTER = Pattern.compile(".+\\.(?:doc|docx|ppt|pptx)(?:\\?.+)?$");	// Doc-extensions which are currently unsupported.
	
	public static final Pattern PAGE_FILE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|mov|pt|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|mso|dtl|svg|asc|txt|c|cc|cxx|cpp|java|py)(?:\\?.+)?$");
	
	public static final Pattern INNER_LINKS_KEYWORDS_FILTER = Pattern.compile(".*(?:doi.org|mailto:|\\?lang=|isallowed=n).*");	// Plain key-words inside innerLinks-String. We avoid "doi.org" in inner links, as, after many redirects, they will reach the same pageUrl.
	
	public static final Pattern INNER_LINKS_FILE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|mov|pt|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|mso|dtl|svg|do|asc|txt|c|cc|cxx|cpp|java|py)(?:\\?.+)?$");
	// In the above, don't include .php and relative extensions, since even this can be a docUrl. For example: https://www.dovepress.com/getfile.php?fileID=5337
	
	// So, we make a new REGEX for these extensions, this time, without a potential argument in the end (e.g. ?id=XXX..), except for the potential "lang".
	public static final Pattern PLAIN_PAGE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:php|php2|php3|php4|php5|phtml|htm|html|shtml|xht|xhtm|xhtml|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|aspx|asp|jsp|do|asc)$");
	
	public static final Pattern INNER_LINKS_FILE_FORMAT_FILTER = Pattern.compile(".+format=(?:xml|htm|html|shtml|xht|xhtm|xhtml).*");
	
	public static final Pattern SPECIFIC_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/.*(?:google|goo.gl|gstatic|facebook|twitter|youtube|linkedin|wordpress|s.w.org|ebay|bing|amazon\\.|wikipedia|myspace|yahoo|mail|pinterest|reddit|blog|tumblr"
			+ "|evernote|skype|microsoft|adobe|buffer|digg|stumbleupon|addthis|delicious|dailymotion|gostats|blogger|copyright|friendfeed|newsvine|telegram|getpocket"
			+ "|flipboard|instapaper|line.me|telegram|vk|ok.rudouban|baidu|qzone|xing|renren|weibo|doubleclick|github).*\\/.*");
	
	public static final Pattern PLAIN_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/[\\w.:-]+(?:\\/)?$");	// Exclude plain domains' urls.
	
	/*
	public static final Pattern SCIENCEDIRECT_DOMAINS = Pattern.compile(".+:\\/\\/.*(?:sciencedirect|linkinghub.elsevier)(?:.com\\/.+)");
	public static final Pattern DOI_ORG_J_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/[j]\\..+");	// doi.org urls which has this form and redirect to "sciencedirect.com".
	public static final Pattern DOI_ORG_PARENTHESIS_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/[\\w]*[\\d]{4}\\-[\\d]{3}(?:[\\d]|[\\w])[\\(][\\d]{2}[\\)][\\d]{5}\\-(?:[\\d]|[\\w])");	// Same reason as above.
	public static final Pattern DOI_ORG_JTO_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/.*[jto]\\..+");	// doi.org urls which has this form and redirect to "sciencedirect.com".
	*/
	
	// Counters for certain unwanted domains. We show statistics in the end.
	public static int javascriptPageUrls = 0;
	//public static int sciencedirectUrls = 0;
	public static int elsevierUnwantedUrls = 0;
	public static int crawlerSensitiveDomains = 0;
	public static int doajResultPageUrls = 0;
	public static int pagesWithHtmlDocUrls = 0;
	public static int pagesRequireLoginToAccessDocFiles = 0;
	public static int pagesWithLargerCrawlingDepth = 0;	// Pages with their docUrl behind an inner "view" page.
	public static int longToRespondUrls = 0;	// Urls belonging to domains which take too long to respon
	public static int urlsWithUnwantedForm = 0;	// (plain domains, unwanted page-extensions ect.)
	public static int pangaeaUrls = 0;	// These urls are in false form by default, but even if they weren't or we transform them, PANGAEA. only gives datasets, not fulltext.
	public static int pagesNotProvidingDocUrls = 0;
	
	
	/**
	 * This method takes the "retrievedUrl" from the inputFile and the "lowerCaseUrl" that comes out the retrieved one.
	 * It then checks if the "lowerCaseUrl" matched certain criteria representing the unwanted urls' types. It uses the "retrievedUrl" for proper logging.
	 * If these criteria match, then it logs the url and returns "true", otherwise, it returns "false".
	 * @param urlId
	 * @param lowerCaseUrl
	 * @return true/false
	 */
	public static boolean matchesUnwantedUrlType(String urlId, String retrievedUrl, String lowerCaseUrl)
	{
		if ( lowerCaseUrl.contains("frontiersin.org") || lowerCaseUrl.contains("tandfonline.com") ) {	// Avoid JavaScript-powered domains, other than the "sciencedirect.com", which is counted separately.
			javascriptPageUrls++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a JavaScript-using domain, other than.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("www.elsevier.com") || lowerCaseUrl.contains("journals.elsevier.com") ) {	// The plain "www.elsevier.com" and the "journals.elsevier.com" don't give docUrls.
			// The "linkinghub.elsevier.com" is redirecting to "sciencedirect.com".
			// Note that we still accept the "elsevier.es" pageUrls, which give docUrls.
			elsevierUnwantedUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the unwanted 'elsevier.com' domain.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("europepmc.org") || lowerCaseUrl.contains("ncbi.nlm.nih.gov") ) {	// Avoid known-crawler-sensitive domains.
			crawlerSensitiveDomains ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a crawler-sensitive domain.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("doaj.org/toc/") ) {	// Avoid resultPages.
			doajResultPageUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the Results-directory: 'doaj.org/toc/'.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("dlib.org") || lowerCaseUrl.contains("saberes.fcecon.unr.edu.ar") ) {    // Avoid HTML docUrls.
			pagesWithHtmlDocUrls++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to an HTML-docUrls site.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("rivisteweb.it") || lowerCaseUrl.contains("wur.nl") || lowerCaseUrl.contains("remeri.org.mx")
				|| lowerCaseUrl.contains("cam.ac.uk") || lowerCaseUrl.contains("scindeks.ceon.rs") || lowerCaseUrl.contains("egms.de") ) {	// Avoid pages known to not provide docUrls (just metadata).
			pagesNotProvidingDocUrls ++;												// Keep "remeri" subDomain of "org.mx", as the TLD is having a lot of different sites.
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the non docUrls-providing site 'rivisteweb.it'.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("bibliotecadigital.uel.br") || lowerCaseUrl.contains("cepr.org") ) {	// Avoid domains requiring login to access docUrls.
			pagesRequireLoginToAccessDocFiles++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a domain which needs login to access docFiles.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("/view/") || lowerCaseUrl.contains("scielosp.org") || lowerCaseUrl.contains("dk.um.si") || lowerCaseUrl.contains("apospublications.com")
				|| lowerCaseUrl.contains("jorr.org") || lowerCaseUrl.contains("redalyc.org") ) {	// Avoid crawling pages with larger depth (innerPagesToDocUrls or Previews of docUrls).
			pagesWithLargerCrawlingDepth ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to an increasedCrawlingDepth-site.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("doi.org/https://doi.org/") && lowerCaseUrl.contains("pangaea.") ) {	// PANGAEA. urls with problematic form and non docUrl inner links.
			pangaeaUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to 'PANGAEA.' urls with invalid form and non-docUrls in their inner links.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("200.17.137.108") ) {	// Known domains with connectivity problems.
			LoadAndCheckUrls.connProblematicUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to known urls with connectivity problems.", null);
			return true;
		}
		/*else if ( lowerCaseUrl.contains("handle.net") || lowerCaseUrl.contains("doors.doshisha.ac.jp") || lowerCaseUrl.contains("opac-ir.lib.osaka-kyoiku.ac.jp") ) {	// Slow urls (taking more than 3secs to connect).
			longToRespondUrls ++;
			UrlUtils.logTriple(urlId, retrievedUrl,"unreachable", "Discarded after matching to domain, known to take long to respond.", null);
			return true;
		}*/
		else if ( lowerCaseUrl.contains("sharedsitesession") ) {	// either "getSharedSiteSession" or "consumeSharedSiteSession".
			ConnSupportUtils.blockSharedSiteSessionDomain(retrievedUrl, null);
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "It was discarded after participating in a 'sharedSiteSession-redirectionPack'.", null);
			return true;
		}
		/*else if ( UrlUtils.SCIENCEDIRECT_DOMAINS.matcher(lowerCaseUrl).matches()
				|| DOI_ORG_J_FILTER.matcher(lowerCaseUrl).matches() || UrlUtils.DOI_ORG_PARENTHESIS_FILTER.matcher(lowerCaseUrl).matches()
				|| DOI_ORG_JTO_FILTER.matcher(lowerCaseUrl).matches() ) {
			sciencedirectUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to 'sciencedirect.com'-family urls.", null);
			return true;
		}*/
		else if ( shouldNotAcceptPageUrl(retrievedUrl, lowerCaseUrl) ) {
			urlsWithUnwantedForm ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to unwantedType-regex-rules.", null);
			return true;
		}
		else
			return false;
	}
	
	
	/**
	 * This method matches the given pageUrl against general regexes.
	 * It returns "true" if the givenUrl should not be accepted, otherwise, it returns "false".
	 * @param pageUrl
	 * @param lowerCasePageUrl
	 * @return true / false
	 */
	public static boolean shouldNotAcceptPageUrl(String pageUrl, String lowerCasePageUrl)
	{
		String lowerCaseUrl = null;
		
		if ( lowerCasePageUrl == null )
			lowerCaseUrl = pageUrl.toLowerCase();
		else
			lowerCaseUrl = lowerCasePageUrl;	// We might have already done the transformation in the calling method.
		
		return	PLAIN_DOMAIN_FILTER.matcher(lowerCaseUrl).matches() || SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseUrl).matches()
				|| URL_DIRECTORY_FILTER.matcher(lowerCaseUrl).matches() || PAGE_FILE_EXTENSION_FILTER.matcher(lowerCaseUrl).matches()
				|| CURRENTLY_UNSUPPORTED_DOC_EXTENSION_FILTER.matcher(lowerCaseUrl).matches();	// TODO - To be removed when these docExtensions get supported.
	}
	
	
	public static boolean shouldNotAcceptInnerLink(String linkStr, String lowerCaseLink)
	{
		String lowerCaseUrl = null;
		
		if ( lowerCaseLink == null )
			lowerCaseUrl = linkStr.toLowerCase();
		else
			lowerCaseUrl = lowerCaseLink;
		
		return	URL_DIRECTORY_FILTER.matcher(lowerCaseUrl).matches() || INNER_LINKS_KEYWORDS_FILTER.matcher(lowerCaseUrl).matches()
				|| SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseUrl).matches() || PLAIN_DOMAIN_FILTER.matcher(lowerCaseUrl).matches()
				|| INNER_LINKS_FILE_EXTENSION_FILTER.matcher(lowerCaseUrl).matches() || INNER_LINKS_FILE_FORMAT_FILTER.matcher(lowerCaseUrl).matches()
				|| PLAIN_PAGE_EXTENSION_FILTER.matcher(lowerCaseUrl).matches()
				|| CURRENTLY_UNSUPPORTED_DOC_EXTENSION_FILTER.matcher(lowerCaseUrl).matches();	// TODO - To be removed when these docExtensions get supported.
		
		// The following checks are obsolete here, as we already use it inside "visit()" method. Still keep it here, as it makes our intentions clearer.
		// !lowerCaseUrl.contains(referringPageDomain)	// Don't check this link if it belongs in a different domain than the referringPage's one.
	}
	
}