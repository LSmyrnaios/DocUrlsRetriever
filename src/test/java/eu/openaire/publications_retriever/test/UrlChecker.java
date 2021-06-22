package eu.openaire.publications_retriever.test;

import com.google.common.collect.HashMultimap;
import edu.uci.ics.crawler4j.url.URLCanonicalizer;
import eu.openaire.publications_retriever.PublicationsRetriever;
import eu.openaire.publications_retriever.crawler.PageCrawler;
import eu.openaire.publications_retriever.util.file.FileUtils;
import eu.openaire.publications_retriever.util.http.ConnSupportUtils;
import eu.openaire.publications_retriever.util.http.DetectedContentType;
import eu.openaire.publications_retriever.util.http.HttpConnUtils;
import eu.openaire.publications_retriever.util.url.LoaderAndChecker;
import eu.openaire.publications_retriever.util.url.UrlTypeChecker;
import eu.openaire.publications_retriever.util.url.UrlUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;


/**
 * This class contains unit-testing for urls.
 * @author Lampros Smyrnaios
 */
public class UrlChecker {

	private static final Logger logger = LoggerFactory.getLogger(UrlChecker.class);

	@Test
	public void checkUrlConnectivity()
	{
		FileUtils.shouldDownloadDocFiles = false;	// Default is: "true".
		//FileUtils.shouldUseOriginalDocFileNames = true;	// Default is: "false".

		// Here test individual urls.

		ArrayList<String> urlList = new ArrayList<>();

		ConnSupportUtils.setKnownMimeTypes();

		//urlList.add("http://repositorio.ipen.br:8080/xmlui/bitstream/handle/123456789/11176/09808.pdf?sequence=1&isAllowed=y");
		//urlList.add("https://ris.utwente.nl/ws/portalfiles/portal/5118887");
		//urlList.add("http://biblioteca.ucm.es/tesis/19972000/X/0/X0040301.pdf");
		//urlList.add("http://vddb.library.lt/fedora/get/LT-eLABa-0001:E.02~2008~D_20080618_115819-91936/DS.005.0.02.ETD");
		//urlList.add("http://dx.doi.org/10.1016/0042-6989(95)90089-6");
		//urlList.add("https://jual.nipissingu.ca/wp-content/uploads/sites/25/2016/03/v10202.pdf\" rel=\"");
		//urlList.add("https://ac.els-cdn.com/S221478531500694X/1-s2.0-S221478531500694X-main.pdf?_tid=8cce02f3-f78e-4593-9828-87b40fcb4f18&acdnat=1527114470_60086f5255bb56d2eb01950734b17fb1");
		//urlList.add("http://www.teses.usp.br/teses/disponiveis/5/5160/tde-08092009-112640/pt-br.php");
		//urlList.add("http://www.lib.kobe-u.ac.jp/infolib/meta_pub/G0000003kernel_81004636");
		//urlList.add("https://link.springer.com/article/10.1186/s12889-016-3866-3");
		//urlList.add("http://ajcmicrob.com/en/index.html");
		//urlList.add("http://kar.kent.ac.uk/57872/1/Fudge-Modern_slavery_%26_migrant_workers.pdf");
		//urlList.add("http://summit.sfu.ca/item/12554");	// MetaDocUrl.
		//urlList.add("http://www.journal.ac/sub/view2/273");
		//urlList.add("https://docs.lib.purdue.edu/cgi/viewcontent.cgi?referer&httpsredir=1&params=%2Fcontext%2Fphysics_articles%2Farticle%2F1964%2Ftype%2Fnative%2F&path_info");
		//urlList.add("http://epic.awi.de/5818/");
		//urlList.add("http://eprints.rclis.org/11525/");
		//urlList.add("https://doors.doshisha.ac.jp/duar/repository/ir/127/?lang=0");	// This case is providing a docUrl but we can't find it!
		//urlList.add("https://engine.surfconext.nl/authentication/idp/single-sign-on?SAMLRequest=fZLBToNAEIbvPgXZOyyLbVM2habaNJpobAp68LaFga6BWdxZGh9fpG3Ugx43%2BefbP%2FPNYvnRNt4RLGmDCRNByDzAwpQa64Q95xt%2Fzpbp1YJU20SdXPXugDt474GctyIC64a5W4PUt2AzsEddwPPuIWEH5zqSnHe9hcD2dYANV2WrkX%2BheJY9cdVoRWOAeesBqFG5scVlFrDWCAH1tioMwocbIUMFQKeLMcx12XEayjbgk67RN8i8jbEFjFUTVqmGBv79OmFKzMpyGgnxNpkf4tm1mNdiWk73U13Hh6oaQrRVRPoI32NEPdwjOYUuYVEoYj8UvghzMZGTuQxnQTyLX5m3tcaZwjQ3Gk%2Bb6y1Ko0iTRNUCSVfIbPX4IKMglPtTiORdnm%2F97VOWM%2B%2FlYiD6MjA4QZKnnf%2FP6s4fs%2FSkSI6N7U%2FC%2FwB1kcjSP5Ut%2BE92en7%2Bvob0Ew%3D%3D&SigAlg=http%3A%2F%2Fwww.w3.org%2F2000%2F09%2Fxmldsig%23rsa-sha1&Signature=bUnOAaMLkaAT9dgvgntSvE0Sg4VaZXphPaYefmumeVGStqfdh9Gucd%2BfVpEHEP1IUmnPsY%2FXRAS%2FieNmfptxetxfOUpfgrBWkbmIRoth95N2p3PJAAQbrX0Mz2AtCpQ0%2BHXJ%2BgSyVrv%2BZVKQkf%2F6SySMcFovyngpvwovZzGmQ4psf%2F0uY1B1aifJ0X2zlxnUmTJWA3Guk1ucQGqTAaTl0DJwn%2BlfS01kJvRpLVtt4ecnFBx%2FZg8Yl7BmqpBiTJgw%2BQFHIIl%2B7fRBpe9uU%2FlnUPsqvDBGUbS6rUce8IImSV%2BjWyB8yryeUzWrWhKUvvemwBOalBp5FLm5eVkN0GqSBw%3D%3D");
			// Problematic Science-Direct urls....
		//urlList.add("https://linkinghub.elsevier.com/retrieve/pii/S0890540184710054");
		//urlList.add("https://linkinghub.elsevier.com/retrieve/pii/S0002929707623672");
		//urlList.add("https://linkinghub.elsevier.com/retrieve/pii/S0042682297988747");
		//urlList.add("https://www.sciencedirect.com/science/article/pii/S0042682297988747?via%3Dihub");
		//urlList.add("https://www.sciencedirect.com/science/article/pii/S221478531500694X?via%3Dihub");
		//urlList.add("https://www.sciencedirect.com/science/article/pii/S221478531500694X/pdf?md5=580457b09a692401774fe0069b8ca507&amp;pid=1-s2.0-S221478531500694X-main.pdf");
		//urlList.add("https://www.sciencedirect.com/science?_ob=MImg&_imagekey=B6TXW-4CCNV6H-1-1G&_cdi=5601&_user=532038&_orig=browse&_coverDate=06%2F30%2F2004&_sk=999549986&view=c&wchp=dGLbVtz-zSkzS&md5=134f1be3418b6d6bdf0325c19562a489&ie=/sdarticle.pdf");
			// .....
		//urlList.add("http://vddb.library.lt/fedora/get/LT-eLABa-0001:E.02~2006~D_20081203_194425-33518/DS.005.0.01.ETD");
		//urlList.add("http://darwin.bth.rwth-aachen.de/opus3/volltexte/2008/2605/");
		//urlList.add("http://publikationen.ub.uni-frankfurt.de/frontdoor/index/index/docId/26920");
		//urlList.add("http://www.grid.uns.ac.rs/jged/download.php?fid=108");
		//urlList.add("http://www.esocialsciences.org/Download/repecDownload.aspx?fname=Document18112005270.6813013.doc&fcategory=Articles&AId=236&fref=repec");
		//urlList.add("https://wwwfr.uni.lu/content/download/35522/427398/file/2011-05%20-%20Demographic%20trends%20and%20international%20capital%20flows%20in%20an%20integrated%20world.pdf");
		//urlList.add("http://www.grid.uns.ac.rs/jged/download.php?fid=108");
		//urlList.add("https://wwwfr.uni.lu/content/download/35522/427398/file/2011-05%20-%20Demographic%20trends%20and%20international%20capital%20flows%20in%20an%20integrated%20world.pdf");
		//urlList.add("https://www.scribd.com/document/397997565/Document-2-Kdashnk");
		//urlList.add("https://stella.repo.nii.ac.jp/?action=pages_view_main&active_action=repository_view_main_item_detail&item_id=103&item_no=1&page_id=13&block_id=21");
		//urlList.add("https://hal.archives-ouvertes.fr/hal-00328350");
		//urlList.add("https://www.clim-past-discuss.net/8/3043/2012/cpd-8-3043-2012.html");
		//urlList.add("http://www.nature.com/cdd/journal/v22/n3/pdf/cdd2014169a.pdf");
		//urlList.add("https://www.ssoar.info/ssoar/handle/document/20820");
		//urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/11500/FascinatE-D1.1.1-Requirements.pdf?sequence=1&isAllowed=y");
		//urlList.add("https://gala.gre.ac.uk/id/eprint/11492/1/11492_Digges_Marketing%20of%20banana%20%28working%20paper%29%201994.pdf");
		//urlList.add("https://zenodo.org/record/1157336");
		//urlList.add("https://zenodo.org/record/1157336/files/Impact%20of%20Biofield%20Energy%20Treated%20%28The%20Trivedi%20Effect%C2%AE%29%20Herbomineral%20Formulation%20on%20the%20Immune%20Biomarkers%20and%20Blood%20Related%20Parameters%20of%20Female%20Sprague%20Dawley%20Rats.pdf");
		//urlList.add("http://amcor.asahikawa-med.ac.jp/modules/xoonips/download.php?file_id=3140");
		//urlList.add("https://orbit.dtu.dk/en/publications/id(994b4e70-ab61-4965-b60c-3a412c5e4031).html");
		//urlList.add("http://eprints.gla.ac.uk/4107/1/pubmedredirect.html");
		//urlList.add("http://dx.doi.org/10.1002/(SICI)1098-2353(2000)13:2<94::AID-CA4>3.0.CO;2-O");
		//urlList.add("https://www.jstor.org/fcgi-bin/jstor/listjournal.fcg/08939454");
		//urlList.add("http://www.lib.kobe-u.ac.jp/infolib/meta_pub/G0000003kernel_DS200004");
		//urlList.add("https://teapot.lib.ocha.ac.jp/?action=pages_view_main&active_action=repository_view_main_item_detail&item_id=29988&item_no=1&page_id=64&block_id=115");	// Problematic meta-url, no special care needed.
		//urlList.add("https://repository.tku.ac.jp/dspace/handle/11150/1477");	// Djvu file.
		//urlList.add("https://upcommons.upc.edu/handle/2117/15648");
		//urlList.add("https://kanagawa-u.repo.nii.ac.jp/?action=repository_action_common_download&item_id=1079&item_no=1&attribute_id=18&file_no=1");

		//urlList.add("http://www.redalyc.org/articulo.oa?id=11182603");	// I had it in "larger-depth"-pages so I was blocking it.. but now they have added a link right away..
				// The only problem now is that I cannot retrieve the content-type/disposition.. of that page.. so..?

		//urlList.add("https://edoc.rki.de/handle/176904/5542");	// It contains a docUrl.. BUT.. "No links were able to be retrieved. Its contentType is: text/html;charset=utf-8"

		//urlList.add("http://dro.dur.ac.uk/1832/");	// Taking 15 seconds not giving any docUrl because it doesn't have any..! TODO - Maybe  set a time limit for every pageUrl?

		//urlList.add("http://edepot.wur.nl/22358");

		//urlList.add("http://www.redalyc.org/articulo.oa?id=11182603");

		//urlList.add("https://www.redalyc.org/articulo.oa?id=10401515");	// There's a content-type issue.

		//urlList.add("https://www.redalyc.org/pdf/104/10401515.pdf#page=1&zoom=auto,-13,792");	// There's a content-type issue.

		//urlList.add("https://www.theseus.fi/handle/10024/19064");	// TODO - Take a look at that.

		//urlList.add("http://repository.usu.ac.id/handle/123456789/6401");
		//urlList.add("http://repository.usu.ac.id/bitstream/handle/123456789/6401/paru-siti noorcahyati.pdf;jsessionid=D2DC32D6655B264360633DA08B04387B?sequence=1");
		//urlList.add("http://repository.usu.ac.id/bitstream/handle/123456789/6401/paru-siti noorcahyati.pdf?sequence=1");
		//urlList.add("http://repository.usu.ac.id/bitstream/handle/123456789/6401/paru-siti%20noorcahyati.pdf");

		//urlList.add("http://www.eumed.net/cursecon/ecolat/mx/mebb-banca.htm");

		//urlList.add("https://essuir.sumdu.edu.ua/handle/123456789/11179");	// It exists "visit" without any error-message.. (it contains a ".docx" which of course we don't handle yet.. but still why no error-message about not finding a dcoUrl?!)


		//urlList.add("http://www.ampere.cnrs.fr/amp-corr1084.html"); 	// This has dynamic links --> block the domain automatically.
		//urlList.add("http://www.ampere.cnrs.fr/correspondance/rdf/ampcorr-<? print $val['bookId'] ?>-RDF.xml"); 	// This is a dynamic PHP link

		//urlList.add("https://infoscience.epfl.ch/record/200665"); // TODO - FIX: IT HAS A DOC-URL BUT WE CANNOT RETRIEVE IT! (there are others like this also!!)
		//urlList.add("https://os.zhdk.cloud.switch.ch/tind-tmp-epfl/4a1abe8c-e663-48ca-a4ed-f88b698d5bf4?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%273418624512cal042.pdf&response-content-type=application%2Fpdf&AWSAccessKeyId=ded3589a13b4450889b2f728d54861a6&Expires=1596163467&Signature=A0o11kht1Ufu2e4OTR2%2BiwTb9j8%3D");
		// The above two urs were fixed..

		//urlList.add("http://doc.rero.ch/record/10569?ln=fr");	// TODO - Fix: No internal links retrieved?!

		//urlList.add("https://gala.gre.ac.uk/id/eprint/11492/");	// TODO - Problem with url.

		//urlList.add("http://gala.gre.ac.uk/id/eprint/11492/1/11492_Digges_Marketing%20of%20banana%20%28working%20paper%29%201994.pdf");

		//urlList.add("https://dx.doi.org/10.1109/ACCESS.2020.2973771");

		//urlList.add("https://ieeexplore.ieee.org/document/8998177");

		//urlList.add("https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=8998177");

		//urlList.add("https://upcommons.upc.edu/handle/2117/20502");	// This takes 20 seconds.. just to find.. nothing..

		//urlList.add("https://www.competitionpolicyinternational.com/from-collective-dominance-to-coordinated-effects-in-eu-competition-policy/");	// This takes 48 seconds to find nothing!!

		//urlList.add("http://kups.ub.uni-koeln.de/1052/");

		//urlList.add("https://www.ssoar.info/ssoar/handle/document/20820");

		//urlList.add("https://doc.rero.ch/record/10569?ln=fr");	// When retrieved from file, the docUrl is not found..

		//urlList.add("http://dspace.library.uu.nl/handle/1874/342676");

		//urlList.add("https://kindai.repo.nii.ac.jp/?action=pages_view_main&active_action=repository_view_main_item_detail&item_id=4050&item_no=1&page_id=13&block_id=21");
		//urlList.add("https://kindai.repo.nii.ac.jp/?action=repository_action_common_download&amp%3Bitem_id=4050&amp%3Bitem_no=1&amp%3Battribute_id=40&amp%3Bfile_no=1");
		//urlList.add("https://kindai.repo.nii.ac.jp/?action=repository_action_common_download&item_id=4050&item_no=1&attribute_id=40&file_no=1");

		//urlList.add("https://redcross.repo.nii.ac.jp/?action=pages_view_main&active_action=repository_view_main_item_detail&item_id=2010&item_no=1&page_id=13&block_id=17");
		//urlList.add("https://redcross.repo.nii.ac.jp/?action=repository_action_common_download&item_id=2010&item_no=1&attribute_id=17&file_no=1");

		//urlList.add("https://edoc.hu-berlin.de/handle/18452/16660");

		//urlList.add("https://halshs.archives-ouvertes.fr/search/index/q/*/structId_i/300415");

		//urlList.add("https://docs.lib.purdue.edu/jtrp/124/");

		//urlList.add("https://figshare.com/articles/Information_Elevated_Join_the_Utah_Society_of_Health_Sciences_Librarians/4126869");

		//urlList.add("https://ora.ox.ac.uk/objects/uuid:0b69b409-46e5-41ac-88c4-f1cfaf08ad24");
		//urlList.add("https://ora.ox.ac.uk/objects/uuid:0b69b409-46e5-41ac-88c4-f1cfaf08ad24/download_file?safe_filename=thesis.pdf&amp%3Bfile_format=application%2Fpdf&amp%3Btype_of_work=Thesis");

		//urlList.add("https://figshare.com/articles/Workshop_quasi_experimental_session_2/4003533");

		//urlList.add("https://www.sciencedirect.com/science/article/pii/S1878535217300990");

		//urlList.add("https://linkinghub.elsevier.com/retrieve/pii/S0022123601937437");

		//urlList.add("https://www.sciencedirect.com/science/article/pii/S0006349504736954/pdfft?md5=35c5059e437f5e5a834fd9b56803ed6f&pid=1-s2.0-S0006349504736954-main.pdf");

		//urlList.add("https://www.sciencedirect.com/science/article/pii/S0006349504736954/pdf");
		//urlList.add("https://www.sciencedirect.com/science/article/pii/S0006349504736954/pdfft");

		//urlList.add("https://www.sciencedirect.com/science/article/pii/S0006349504736954/pdfft?&pid=1-s2.0-S0006349504736954-main.pdf");

		//urlList.add("https://pdf.sciencedirectassets.com/277708/1-s2.0-S0006349504X70855/1-s2.0-S0006349504736954/main.pdf?X-Amz-Security-Token=IQoJb3JpZ2luX2VjEOL%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCXVzLWVhc3QtMSJHMEUCIGdaSW2u9CmMjXtA5569GcBxUnZbpjLSf%2BcIt8En2slEAiEAp3AtQWwhaONtpvhCGnEGcj%2FGS%2B%2F4ZZ66Ts5C3mC7BcgqtAMIGxADGgwwNTkwMDM1NDY4NjUiDKHCCPYoIAIBrzMTeCqRAy2XwSpdFCFq0f1DRzV7K3z%2Fu%2B0WYYsqdRgR2aFnpEt5sSmylwE65A%2FmCIwE8KFCu%2BlPZJxm8c8tXe6ezo9EnxfLuytyQ3%2FZu67QBpbdIfnnvWxLyS8NCz7PRLgGwqLZgeifradDbPsX8mr0xVNj1lP6WGRg0q8DCX87T3pHrbcjq5KdLwkyEnPSZ0TZ33UuyFgmtFSq2ueHnILLOyNp4ZOLCpHtbshezJk7UqP23JfDnjjcgCue7SLyYymWhDynlFuFmhL93iew0bDKHfXGk1znuQsMU1HfRkUfWVCMPjjX8KoHo6uOqrn9b%2BDraZ1h%2BG%2FTVa0VCaOiDQo3%2Fhgm2L8Vs%2Bu3OlZ27yJBRCIp5%2Fyl3eXhdcYKKmRtFi4bN%2Bn%2Bt2MMRNXkCX8XhByFieG8d7RDIKRDJ0gjZDF0yHuUV2zO1oFSzCGLMuW3sSiwinTVfn%2FEUX1R7Ra22fQbFs9Bqx32PEabtvwr6ca4vlTNqbrqSeeM%2B065d61yd2fDWL67tBpJ2jo6D9duik2OE33a34L4MP3%2F9%2FsFOusBJnbVh1zfoKZbI%2FdRiPhb8y%2FjLzc%2FZDOmgiQXY2pA6vKGsZqk3%2FlZfZMhDZpL2l03xgLBrlb5KM6gwTjnU9EembL1wI03Hv0pDCbz0kY22sDQfk%2Bb3VlM3n9tJXkp8g5POA1n6bo3%2FyljgyC%2F5jkrzfYWf3%2Fxbp%2FXRL9zrBVom17%2FSF0yF1vK93H7Nr0ku5LB4tWC%2BnKEJNYzZvgwrVoRlH0wI%2BDqwdJ%2F%2BNFot00SAwDhn34gqrjh%2BfZXUgWDkd%2BwRW2Cd3aB%2FC9e%2BkEmL6jckQvowOSS58VsCPwuYCfjTtfe1ovD8HqeaT3Rjw%3D%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20201007T190115Z&X-Amz-SignedHeaders=host&X-Amz-Expires=300&X-Amz-Credential=ASIAQ3PHCVTYTCBRFDLU%2F20201007%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Signature=df39650f9b6c118a960c729ef13fa03edf484be34dfa04b5b7444471b84ef3a5&hash=3c93b0d927e918dfda271944e63aeb492ac0095674d5c343499c2ed885bdf771&host=68042c943591013ac2b2430a89b270f6af2c76d8dfd086a07176afe7c76c2c61&pii=S0006349504736954&tid=spdf-af2ae9a8-9ca3-4af0-aae6-416c7691676d&sid=5b59c41258a9844c232aea01dd83a1967ae2gxrqb&type=client");

		//urlList.add("http://doc.rero.ch/record/10569?ln=fr");

		//urlList.add("https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2151466/");

		//urlList.add("https://ageconsearch.umn.edu/record/32227");	// This redirects to the below url..
		//urlList.add("https://tind-customer-agecon.s3.amazonaws.com/6c23999d-49ce-497c-aa7c-354f2248e709?response-content-disposition=attachment%3B+filename*%3DUTF-8%27%2712020198.pdf&response-content-type=application%2Fpdf&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=86400&X-Amz-Credential=AKIAXL7W7Q3XHXDVDQYS%2F20201008%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-SignedHeaders=host&X-Amz-Date=20201008T182613Z&X-Amz-Signature=e61410cc4970eaedcde14f6c2b0c0a24b1921c13122b156b11cf91ef0132e619");

		//urlList.add("https://www.agulin.aoyama.ac.jp/repo/repository/1000/18790/");

		//urlList.add("https://hal.archives-ouvertes.fr/hal-01509447/");

		//urlList.add("http://t2r2.star.titech.ac.jp/cgi-bin/publicationinfo.cgi?q_publication_content_number=CTT100485154");

		//urlList.add("http://ir.lib.u-ryukyu.ac.jp/handle/20.500.12000/17809");

		//urlList.add("https://iris.polito.it/handle/11583/2498245");

		//urlList.add("https://www.competitionpolicyinternational.com/from-collective-dominance-to-coordinated-effects-in-eu-competition-policy/");

		/*urlList.add("https://tind-customer-agecon.s3.amazonaws.com/2a3f774f-a605-47af-9d48-914611c3d35e?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%2769.1.pdf&response-content-type=application%2Fpdf&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=86400&X-Amz-Credential=AKIAXL7W7Q3XHXDVDQYS%2F20201101%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-SignedHeaders=host&X-Amz-Date=20201101T145901Z&X-Amz-Signature=f8e78bb6e5231fd9fafc4ad81e2a9167c6977079345491453f5e4fdab9fb9e58");

		urlList.add("https://www.scielo.br/scielo.php?script=sci_arttext&pid=S0102-79722007000100019");*/

		//urlList.add("https://academic.microsoft.com/#/detail/2945595536");

/*		urlList.add("https://www.ingentaconnect.com/content/cscript/cvia/2017/00000002/00000003/art00008");	// special "data-popup" links

		urlList.add("https://academic.microsoft.com/paper/2945595536/related/search?q=Implementation%20of%20Integrated%20Learning%20Based%20Integrated%20Islamic%20School%20Network%20Curriculum%20in%20SMA%20ABBS%20Surakarta&qe=Or(Id%253D2739664616%252CId%253D2373811030%252CId%253D2898003834%252CId%253D3044850778%252CId%253D3045496101%252CId%253D1925808456%252CId%253D2918948984%252CId%253D2789335018%252CId%253D2809158637%252CId%253D2558929148%252CId%253D3028122032%252CId%253D3018812449%252CId%253D2912559925%252CId%253D2138593466%252CId%253D2058345642%252CId%253D2099519838%252CId%253D2890928310%252CId%253D2170924641%252CId%253D2055300576%252CId%253D2277303564)&f=&orderBy=0");

		urlList.add("https://europepmc.org/article/PMC/3258478");
		urlList.add("https://europepmc.org/articles/PMC3061194/");

		urlList.add("https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6473510/");
		urlList.add("https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3442177/");*/

/*
		urlList.add("https://www.atlantis-press.com/journals/artres/125928993");

		urlList.add("https://www.sciencedirect.com/science/article/pii/S1743919113008480/pdf");

		urlList.add("https://pubmed.ncbi.nlm.nih.gov/1461747/");

		urlList.add("https://europepmc.org/backend/ptpmcrender.fcgi?accid=PMC2137806&blobtype=pdf");

		urlList.add("https://bdjur.stj.jus.br/dspace/handle/2011/86443");

		urlList.add("https://academic.microsoft.com/#/detail/2084896083");	// TODO - Take care of this..  it's a canonicalization error from crawler4j..

		urlList.add("https://academic.microsoft.com/api/entity/1585286892?entityType=2");
*/

		//urlList.add("http://www.dlib.si/details/URN:NBN:SI:doc-U2Y13NIM");	// It just wants the page's cookie.. but it's a big rewrite to handle that.
		//urlList.add("http://www.dlib.si/stream/URN:NBN:SI:doc-U2Y13NIM/48201ba2-ddbb-4701-9633-5c20472be286/PDF");

		//urlList.add("https://www.scielo.br/scielo.php?script=sci_arttext&pid=S0100-40422006000300022");

		//urlList.add("https://biblio.ugent.be/publication/8526773");

		//urlList.add("http://www.dlib.si/details/URN:NBN:SI:doc-2H0X3VLC");

		//urlList.add("https://arrow.tudublin.ie/hwork17/13/");

		//urlList.add("https://pub.uni-bielefeld.de/record/2288649");

		/*urlList.add("https://www.documentation.ird.fr/hor/fdi:010063541");
		urlList.add("https://www.documentation.ird.fr/hor/fdi:27250#");*/

		//urlList.add("https://pubs.rsc.org/en/content/articlelanding/2014/ra/c4ra01269k/unauth");

		//urlList.add("http://www.scielo.br/scielo.php?script=sci_arttext&pid=S0100-54052010000300005&lng=pt&tlng=pt");

		//urlList.add("https://bg.copernicus.org/preprints/bg-2019-198/");
		//urlList.add("https://bg.copernicus.org/articles/17/1/2020/bg-17-1-2020-discussion.html");

		//urlList.add("http://www.emeraldinsight.com/Insight/viewContentItem.do;jsessionid=18BBC43455023514C4471735091C2F76?contentType=Article&contentId=847882");

		//urlList.add("https://www.osapublishing.org/boe/fulltext.cfm?uri=boe-8-10-4621&id=373239");

		//urlList.add("https://projecteuclid.org/download/pdf_1/euclid.tmj/1178227427");

		//urlList.add("https://www.nature.com/articles/eye2017223.pdf");	// TODO - It get's blocked..

		//urlList.add("https://academic.microsoft.com/#/detail/2904721780");
		//urlList.add("https://academic.microsoft.com/#/detail/2064491651");
		//urlList.add("https://academic.microsoft.com/api/entity/2936128612?entityType=2");

		//urlList.add("https://academic.microsoft.com/#/detail/2439620103");

		// TODO - Check for an offline redirector for "frontiersin.org"
		// It has to contain "article(?:s)?" and end with "full".. otherwise the redirection fails... (we should block it if it cannot redirect: throw an exception)
		// Remove the blocking rule in "UrlTypeChecker.matchesUnwantedUrlType()"..
		/*urlList.add("https://www.frontiersin.org/articles/10.3389/fphys.2019.01105/full");
		urlList.add("https://www.frontiersin.org/articles/10.3389/fphys.2019.01105/pdf");

		urlList.add("https://www.frontiersin.org/article/10.3389/fbioe.2020.00882/full");
		urlList.add("https://www.frontiersin.org/article/10.3389/fbioe.2020.00882/pdf");

		urlList.add("http://www.frontiersin.org/10.3389/conf.fnbeh.2012.27.00319/event_abstract");
		urlList.add("http://www.frontiersin.org/10.3389/conf.fnbeh.2012.27.00319/pdf");

		urlList.add("http://www.frontiersin.org/articles/10.3389/conf.fnbeh.2012.27.00319/event_abstract");
		urlList.add("http://www.frontiersin.org/articles/10.3389/conf.fnbeh.2012.27.00319/pdf");*/


		//////////////////////////////
		//urlList.add("https://www.openrepository.ru/article?id=254577");	// Takes 3 minutes and 13 seconds only to give no docUrl!! -- SOLVED with links-limit.

		//urlList.add("https://www.documentation.ird.fr/hor/fdi:010063541");

		//urlList.add("https://www.jceionline.org/download/evaluation-of-the-pain-and-foot-functions-in-women-with-hallux-valgus-deformities-3757.pdf"); //memeType="Application/pdf"

		//urlList.add("http://hdl.handle.net/2060/20040034066");
		// 2020-12-15 21:42:43.698 WARN  e.o.d.util.http.ConnSupportUtils.printEmbeddedExceptionMessage(@561) - [HttpConnUtils.java->handleRedirects(@494)] - Url: "http://hdl.handle.net/2060/20040034066" was prevented to redirect to the unwanted location: "https://ntrs.nasa.gov/citations/20040034066", after receiving an "HTTP 302" Redirect Code.
		//urlList.add("https://ntrs.nasa.gov/citations/20040034066");	// I made an exception in the regex-rule just for this site, but it does not give links.
		// TODO - It's really easy to "guess" the docUrl.. just add a special handler. the docUrl is:  https://ntrs.nasa.gov/api/citations/20040034066/downloads/20040034066.pdf - DONE
		//urlList.add("https://ntrs.nasa.gov/api/citations/20040034066/downloads/20040034066.pdf");

		//urlList.add("https://api.elsevier.com/content/article/PII:S2214999617304289?httpAccept=text/plain");
		//urlList.add("https://api.elsevier.com/content/article/PII:S2214999617304289");

		//urlList.add("https://dergipark.org.tr/tr/download/article-file/566137");

		/*urlList.add("/search/author:\"Lath, A.\"");
		urlList.add("/search/author\"Lath, A.\"");
		urlList.add("/search/author:Lath, A.");
		urlList.add("/search/author Lath, A.");
		urlList.add("/search/author Lath. A.");
		urlList.add("/search/author_Lath_A");*/

		//urlList.add("https://www.osti.gov/biblio/1398792");
		//urlList.add("https://www.osti.gov/servlets/purl/1398792");


		//urlList.add("https://infoscience.epfl.ch/record/53752");

		//urlList.add("https://repositorio.uam.es/handle/10486/687988");

		//urlList.add("https://bv.fapesp.br/pt/publicacao/96198/homogeneous-gaussian-profile-p-type-emitters-updated-param/");

		//urlList.add("http://www.ccsenet.org/journal/index.php/ijb/article/download/48805/26704");

		//urlList.add("https://gh.copernicus.org/articles/6/276/1951/gh-6-276-1951.pdf");

		//urlList.add("https://checklist.pensoft.net/article/18320/");

		//urlList.add("https://www.osti.gov/pages/biblio/1487179-injection-self-consistent-beam-linear-space-charge-force-ring");

		/*urlList.add("https://core.ac.uk/display/91816393");
		urlList.add("https://core.ac.uk/labs/oadiscovery/redirect?url=https://core.ac.uk/download/pdf/193222419.pdf");
		urlList.add("https://core.ac.uk/download/pdf/193222419.pdf");*/


		/*urlList.add("https://bv.fapesp.br/pt/publicacao/96198/homogeneous-gaussian-profile-p-type-emitters-updated-param/");
		urlList.add("https://www.medicaljournals.se/jrm/content/abstract/10.2340/16501977-0797");*/

		//urlList.add("https://figshare.com/articles/Noncollinear_Relativistic_DFT_U_Calculations_of_Actinide_Dioxide_Surfaces/7159676");	// TODO - No "Location" field was found.. (but Firefox handles it) no idea how..

		/*urlList.add("https://academic.microsoft.com/api/entity/1524318892?entityType=2");
		urlList.add("http://www.josonline.org/pdf/v11i1p22.pdf");*/

		//urlList.add("https://personal.utdallas.edu/~tms063000/website/Gaibulloev_Sandler_Kyklos2008.PDF");

		//urlList.add("https://repository.publisso.de/resource/frl:5967710");

		//urlList.add("https://api.elsevier.com/content/article/PII:S1976131717302797?httpAccept=text%2Fxml");

		//urlList.add("http://dspace.library.uu.nl/bitstream/1874/241785/1/Jongbloed%2c%20AW%20-%20%20Verbeurte%20van%20dwangsommen%20onmogelijkheid%20en%20%60eigen%20schuld%27%202011.pdf");

//		urlList.add("https://www.frontiersin.org/articles/10.3389/fsoc.2018.00012/full");
//		urlList.add("https://www.frontiersin.org/articles/10.3389/fsoc.2018.00012/pdf");

		//urlList.add("https://www.bmj.com/content/1/6178/1610.full.pdf");

		//urlList.add("https://riunet.upv.es/bitstream/10251/129858/1/Yacchirema%20-%20Arquitectura%20de%20Interoperabilidad%20de%20dispositivos%20f%c3%adsicos%20para%20el%20%20Internet%20de%20las%20C....pdf");

		//urlList.add("http://eprints.whiterose.ac.uk/122335/1/ethics-0%284%29.pdf");
		//urlList.add("https://periodicos.ufsc.br/index.php/ethic/article/view/17299");

		//urlList.add("https://ieeexplore.ieee.org/document/8752956");

		/*urlList.add("https://www.frontiersin.org/articles/10.3389/fphys.2018.00414/full");
		urlList.add("https://www.frontiersin.org/articles/10.3389/fphys.2018.00414/pdf");
		urlList.add("https://www.frontiersin.org/articles/10.3389/fncel.2019.00494/pdf");

		urlList.add("https://www.frontiersin.org/article/10.3389/feart.2017.00079");
		urlList.add("https://www.frontiersin.org/article/10.3389/feart.2017.00079/full");
		urlList.add("https://www.frontiersin.org/article/10.3389/feart.2017.00079/pdf");
		urlList.add("http://www.frontiersin.org/10.3389/conf.fnbeh.2012.27.00319/event_abstract");

		urlList.add("http://dergipark.gov.tr/beuscitech/issue/40162/477737");
		urlList.add("http://dergipark.org.tr/beuscitech/issue/40162/477737");
		urlList.add("https://dergipark.org.tr/tr/pub/kefdergi/issue/22601/241490");*/

		//urlList.add("https://academic.microsoft.com/#/detail/2386671561");

		//urlList.add("https://thesai.org/Publications/ViewPaper?Volume=9&Issue=1&Code=ijacsa&SerialNo=63");

		//urlList.add("https://springernature.figshare.com/ndownloader/files/10101129");

		//urlList.add("https://zenodo.org/record/2580974");

		//urlList.add("https://zenodo.org/record/1493221");

		//urlList.add("https://zenodo.org/record/1493221/files/summary_Al21_usda.txt?download=1");

		//urlList.add("https://springernature.figshare.com/articles/dataset/Additional_file_4_of_CiliateGEM_an_open-project_and_a_tool_for_predictions_of_ciliate_metabolic_variations_and_experimental_condition_design/7405082/1");

		//urlList.add("https://datadryad.org/stash/dataset/doi:10.5061/dryad.v1c28");
		//urlList.add("https://datadryad.org/stash/downloads/file_stream/56231");

//		urlList.add("https://zenodo.org/record/3407725");
//		urlList.add("https://zenodo.org/record/3407725/files/Survey%20dataset.xlsx?download=1");
//		urlList.add("https://figshare.com/articles/dataset/DICE_H2020_Deliverable_D3_9_Final_version/6478792");
//		urlList.add("https://figshare.com/articles/dataset/Financial_Metrics_Dataset_of_US_companies/7706297");
//
//		urlList.add("https://datadryad.org/stash/dataset/doi:10.5061/dryad.bp76g");
//		urlList.add("https://datadryad.org/stash/downloads/download_resource/8585");
//		urlList.add("https://datadryad.org/stash/downloads/file_stream/56231");
//		urlList.add("https://datadryad.org/stash/dataset/doi:10.5061/dryad.hb8b5");

		//urlList.add("https://zenodo.org/record/3981177");
		//urlList.add("https://springernature.figshare.com/articles/figure/Additional_file_4_of_Development_of_acquired_resistance_to_lapatinib_may_sensitise_HER2-positive_breast_cancer_cells_to_apoptosis_induction_by_obatoclax_and_TRAIL/7193426");

		//urlList.add("https://springernature.figshare.com/articles/dataset/Additional_file_5_of_Biodiversity_and_host-parasite_cophylogeny_of_Sphaerospora_sensu_stricto_Cnidaria_Myxozoa_/6540329");

		//urlList.add("https://zenodo.org/record/173258");

		//urlList.add("https://springernature.figshare.com/articles/figure/Additional_file_6_Figure_S5_of_Recombination_in_pe_ppe_genes_contributes_to_genetic_variation_in_Mycobacterium_tuberculosis_lineages/4471484/1");

		//urlList.add("https://figshare.com/articles/dataset/Data_of_the_publication_Recent_Advances_in_Rare_Earth_Doped_Inorganic_Crystalline_Materials_for_Quantum_Information_Processing/9124499");
		//urlList.add("https://ndownloader.figshare.com/files/16644443");

		//urlList.add("https://zenodo.org/record/1322372");

		//urlList.add("https://figshare.com/articles/dataset/Climefish_DSS_-_West_of_Scotland_SQL_Script_/11889249");

		//urlList.add("https://doi.pangaea.de/10.1594/PANGAEA.897592");

		//urlList.add("https://figshare.com/articles/dataset/16S_rRNA_gene_sequences_alignment_of_a_subset_of_relevant_cyanobacterial_strains/4497041");

		//urlList.add("http://www.dlib.si/details/URN:NBN:SI:doc-U2Y13NIM");
		//urlList.add("http://www.dlib.si/stream/URN:NBN:SI:doc-U2Y13NIM/48201ba2-ddbb-4701-9633-5c20472be286/PDF");

		//urlList.add("https://dspace.library.uu.nl/handle/1874/374412");

		//urlList.add("https://journals.openedition.org/episteme/388");

		//urlList.add("https://projecteuclid.org/euclid.ijm/1385129950");
		//urlList.add("https://projecteuclid.org/journals/illinois-journal-of-mathematics/volume-56/issue-2/Dispersive-estimates-for-matrix-and-scalar-Schr%C3%B6dinger-operators-in-dimension/10.1215/ijm/1385129950.full");

		//urlList.add("https://psyarxiv.com/e9uk7");	// Its a dynamic domain..
		//urlList.add("https://psyarxiv.com/e9uk7/");	// Its a dynamic domain..
		//urlList.add("https://psyarxiv.com/e9uk7/download");	// Its a dynamic domain..

		//urlList.add("https://hdl.handle.net/20.500.12605/4053");	// This should NOT give a PDF...!
		// TODO When retrieving the internalLinks, check for the text around it to make sure it is not sth like "guide" or "manual" or anything like that!
		// Now it's super easy t do that..! :-)


		//urlList.add(" https://strathprints.strath.ac.uk/72504/");
		//urlList.add("https://www.tandfonline.com/doi/pdf/10.1080/00455091.2018.1432398?needAccess=true&");

		//urlList.add("ftp://ftp.esat.kuleuven.be/stadius/vanwaterschoot/reports/19-xx.pdf");

		//urlList.add("http://thredds.d4science.org/thredds/catalog/public/netcdf/AquamapsNative/catalog.html");

		//urlList.add("https://digitalcommons.usu.edu/cgi/viewcontent.cgi?article=1271&amp;context=water_rep");

		//urlList.add("https://academic.microsoft.com/#/detail/1864052074");
		//urlList.add("https://academic.microsoft.com/#/detail/2963616798");
		//urlList.add("https://ieeexplore.ieee.org/document/4282046");

		//urlList.add("https://zenodo.org/record/1145726");

		//urlList.add("https://orbi.uliege.be/bitstream/2268/218222/1/hal-02830219.pdf");
		urlList.add("https://www.sciencedirect.com/science/article/pii/S1687850718300177/pdf");

		logger.info("Urls to check:");
		for ( String url: urlList )
			logger.info(url);

		Instant start = Instant.now();

		for ( String url : urlList )
		{
			String urlToCheck = url;	// Use an extra String or it cannot be printed in the error-logging-message as it will be null.
			if ( !urlToCheck.contains("#/") && (urlToCheck = URLCanonicalizer.getCanonicalURL(url, null, StandardCharsets.UTF_8)) == null ) {
				logger.warn("Could not canonicalize url: " + url);
				continue;
			}

			if ( UrlTypeChecker.matchesUnwantedUrlType(null, urlToCheck, urlToCheck.toLowerCase()) )
				continue;

/*			String urlPath = UrlUtils.getPathStr(urlToCheck, null);
			if ( urlPath == null )
				return;
			else
				logger.debug("urlPath: " + urlPath);*/

			try {
				HttpConnUtils.connectAndCheckMimeType("null", urlToCheck, urlToCheck, urlToCheck, null, true, false);	// Sent the < null > in quotes to avoid an NPE in the concurrent data-structures.
			} catch (Exception e) {
				UrlUtils.logOutputData(null, urlToCheck, null, "unreachable", "Discarded at loading time, due to connectivity problems.", null, true, "true", "true", "false", "false");
			}
		}

		logger.debug("Connection-problematic-urls: " + LoaderAndChecker.connProblematicUrls);
		logger.debug("Content-problematic-urls: " + PageCrawler.contentProblematicUrls);

		PublicationsRetriever.calculateAndPrintElapsedTime(start, Instant.now());
	}


	@Test
	public void checkContentExtraction()
	{
		String url = "http://ajcmi.umsha.ac.ir/";

		try {
			HttpURLConnection conn = HttpConnUtils.handleConnection(null, url, url, url, null, true, false);
			DetectedContentType detConType = ConnSupportUtils.extractContentTypeFromResponseBody(conn);

			if ( detConType == null ) {
				logger.error("Error when extracting the content..");
				return;
			}

			logger.debug(detConType.detectedContentType + " | " + detConType.firstHtmlLine);

			if ( detConType.bufferedReader != null )
				ConnSupportUtils.closeBufferedReader(detConType.bufferedReader);

		} catch ( Exception e ) {
			logger.warn("", e);
		}
	}


	//@Test
	public void checkUrlRegex()
	{
		logger.info("Going to test url-triple-regex on multiple urls..");

		// List contains urls for REGEX-check
		ArrayList<String> urlList = new ArrayList<>();

		urlList.add("http://example.com/path/to/page?name=ferret&color=purple");
		urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/11500/FascinatE-D1.1.1-Requirements.pdf?sequence=1&isAllowed=y");
		urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/11500/?sequence=1&isAllowed=y");
		urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/11500/FascinatE-D1.1.1-Requirements.pdf");
		urlList.add("http://ena.lp.edu.ua:8080/bitstream/ntb/12073/1/17_КОНЦЕПТУАЛЬНІ ЗАСАДИ ФОРМУВАННЯ ДИСТАНЦІЙНОГО.pdf");
		urlList.add("https://hal.archives-ouvertes.fr/hal-01558509/file/locomotion_B&B.pdf");
		urlList.add("https://zenodo.org/record/1157336/files/Impact of Biofield Energy Treated (The Trivedi Effect®) Herbomineral Formulation on the Immune Biomarkers and Blood Related Parameters of Female Sprague Dawley Rats.pdf");
		urlList.add("http://dspace.ou.nl/bitstream/1820/9091/1/Methodological triangulation of the students' use of recorded lectures.pdf");
		urlList.add("http://orca.cf.ac.uk/29804/1/BourneTrust&FinancialElites Ful.pdf");
		urlList.add("https://repository.nwu.ac.za/bitstream/handle/10394/5642/Y&T_2006(SE)_Mthembu.pdf?sequence=1&isAllowed=y");
		urlList.add("http://eprints.nottingham.ac.uk/1718/1/Murphy_&_Whitty,_The_Question_of_Evil_(2006)_FLS.pdf");
		urlList.add("http://paduaresearch.cab.unipd.it/5619/1/BEGHETTO_A_-_L'attività_di_revisione_legale_del_bilancio_d'esercizio.pdf");
		urlList.add("http://paduaresearch.cab.unipd.it/5619/1/BEGHETTO_A_-_L'attivit%C3%A0_di_revisione_legale_del_bilancio_d'esercizio.pdf");
		urlList.add("https://signon.utwente.nl:443/oam/server/obrareq.cgi?encquery%3DNag5hroDAYcZB73s6qFabcJrCLu93LkC%2B%2BehD6VzQDBXjyBeFwtDMuD1y8RrSDHeJy5fC5%2Fy2bJ06QJBGd1f0YAph8D4YcL49l8SbwEcjfrA7TYcvee8aiQakGx1o5pLUN4KrQC%2F3OBf5PrdrMwJb98CJjMkSBGdSMteofa1JVOMTxSQUwTdObMY04eHA51ReEiT3v3fpOlg6%2BcJgtdHSCEhYL2yCt2rgkgPSVoJ%2BqZvFzc6o3FhSmCeXtFiO1FpG5%2BzFSP5JEHVFUerdnw1GpLOtGOT6PpbDf9Fd%2BnAT6Q%3D%20agentid%3DRevProxyWebgate%20ver%3D1%20crmethod%3D2&ECID-Context=1.005YfySQ6km8LunDsnZBCX0002cY00001F%3BkXjE");
		urlList.add("http://www.ampere.cnrs.fr/correspondance/rdf/ampcorr-<? print $val['bookId'] ?>-RDF.xml");
		urlList.add("https:/articles/matecconf/abs/2018/32/matecconf_smima2018_03065/matecconf_smima2018_03065.html");

		// The following are "made-up"..
		urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/115::00/docID:Check?sequence=1&isAllowed=y");
		urlList.add("https://upcommons.upc.edu/bitstream/handle/2117/11500/docID:Check?sequence=1&isAllowed=y");
		// Add more urls to test.

		int regex_problematic_urls = 0;

		for ( String url : urlList )
			if ( !validateRegexOnUrl(url) )
				regex_problematic_urls ++;

		boolean shouldCheckWholeInput = false;

		if ( shouldCheckWholeInput )
		{
			logger.info("Now we are going to check the urls provided in the input-file.");
			TestNonStandardInputOutput.setInputOutput();

			// Start loading and checking urls.
			HashMultimap<String, String> loadedIdUrlPairs;
			boolean isFirstRun = true;
			while ( true )
			{
				loadedIdUrlPairs = FileUtils.getNextIdUrlPairBatchFromJson(); // Take urls from jsonFile.

				if ( LoaderAndChecker.isFinishedLoading(loadedIdUrlPairs.isEmpty(), isFirstRun) )    // Throws RuntimeException which is automatically passed on.
					break;
				else
					isFirstRun = false;

				Set<String> keys = loadedIdUrlPairs.keySet();

				for ( String retrievedId : keys )
					for ( String retrievedUrl : loadedIdUrlPairs.get(retrievedId) )
						if ( !validateRegexOnUrl(retrievedUrl) )
							regex_problematic_urls ++;
			}// End-while
		}

		if ( regex_problematic_urls == 0 )
			logger.info("All of the urls matched with the URL_REGEX.");
		else
			logger.error(regex_problematic_urls + " urls were found to not match with the URL_REGEX..!");
	}
	
	
	private static boolean validateRegexOnUrl(String url)
	{
		logger.info("Checking \"URL_TRIPLE\"-REGEX on url: \"" + url + "\".");

		Matcher urlMatcher = UrlUtils.getUrlMatcher(url);
		if ( urlMatcher == null )
			return false;

		String urlPart = null;
		if ( (urlPart = UrlUtils.getDomainStr(url, urlMatcher)) != null )
			logger.info("\t\tDomain: \"" + urlPart + "\"");
		
		urlPart = null;
		if ( (urlPart = UrlUtils.getPathStr(url, urlMatcher)) != null )
			logger.info("\t\tPath: \"" + urlPart + "\"");
		
		urlPart = null;
		if ( (urlPart = UrlUtils.getDocIdStr(url, urlMatcher)) != null )
			logger.info("\t\tDocID: \"" + urlPart + "\"");

		return true;
	}
}
