# DocUrlsRetriever    [![Build Status](https://travis-ci.com/LSmyrnaios/DocUrlsRetriever.svg?branch=master)](https://travis-ci.com/LSmyrnaios/DocUrlsRetriever)

A Java-program which retrieves the Document and Dataset Urls from the given Publication-Web-Pages and if wanted, it can also download the full-texts.<br>
It is being developed for the European organization: [**OpenAIRE**](https://www.openaire.eu/). <br>
Afterwards, these full-text documents are mined, in order to enrich a much more complete set of OpenAIRE publications with inference links.<br>

The **DocUrlsRetriever** takes as input the PubPages with their IDs -in JSON format- and gives an output -also in JSON format,
which contains the IDs, the PubPages, the Document or Dataset Urls and a comment.<br>
The "comment" can have the following values:
- an empty string, if the document url is retrieved, and the user specified that the document files will not be downloaded
- the information if the resulted url is a dataset url
- the DocFileFullPath, if we have chosen to download the DocFiles
- the ErrorCause, if there was any error which prevented the discovery of the DocUrl (in that case, the DocUrl is set to "unreachable")
<br>

PubPage: *the web page with the publication's information.*<br> 
DocUrl: *the url of the fulltext-document-file.*<br>
DatasetUrl: *the url of the dataset-file.*<br>
Full-text: *the document containing all the text of a publication.*<br>
DocFileFullPath: *the full-storage-path of the fulltext-document-file.*<br>
ErrorCause: *the cause of the failure of retrieving the docUrl or the docFile.*<br>

Sample JSON-input:
```
{"id":"dedup_wf_001::83872a151fd78b045e62275ca626ec94","url":"https://zenodo.org/record/884160"}
```
Sample JSON-output (with downloading of the DocFile):
```
{"id":"dedup_wf_001::83872a151fd78b045e62275ca626ec94","sourceUrl":"https://zenodo.org/record/884160","docUrl":"https://zenodo.org/record/884160/files/Data_for_Policy_2017_paper_55.pdf","comment":"/home/lampros/DocUrlsRetriever/target/../example/sample_output/DocFiles/1.pdf"}
```
<br>

This program utilizes multiple threads to speed up the process, while using politeness delays between same-domain connections, in order to avoid overloading the data-providers.
<br>
In case no IDs are available to be used in the input, the user should provide a file containing just urls (one url per line)
and specify that wishes to process a data-set with no IDs, by changing the "**util.url.LoaderAndChecker.useIdUrlPairs**"-variable to "*false*".
<br>
If you want to run it with distributed execution on multiple VMs, you may give a different starting-number for the docFiles in each instance (see the run-instructions below).<br>
<br>

**Disclaimers**:
- Keep in mind that it's best to run the program for a small set of urls (a few hundred maybe) at first,
    in order to see which parameters work best for you (url-timeouts, domainsBlocking ect.).
- Please note that **DocUrlsRetriever** is currently in **beta**, so you may encounter some issues.<br>
<br>

## Install & Run (using MAVEN)
To install the application, navigate to the directory of the project, where the ***pom.xml*** is located.<br>
Then enter this command in the terminal:<br>
**``mvn install``**<br>

To run the application you should navigate to the ***target*** directory, which will be created by *MAVEN* and run the executable ***JAR*** file,
while choosing the appropriate run-command.<br> 

**Run with standard input/output:**<br>
**``java -jar doc_urls_retriever-0.4-SNAPSHOT.jar arg1:'-inputFileFullPath' arg2:<inputFile> arg3:'-retrieveDataType' arg4:'<dataType: document | dataset | all>' arg5:'-downloadDocFiles' arg6:'-firstDocFileNum' arg7:'NUM' arg8:'-docFilesStorage'
arg9:'storageDir' < stdIn:'inputJsonFile' > stdOut:'outputJsonFile'``**<br>

**Run tests with custom input/output:**
- Inside ***pom.xml***, change the **mainClass** of **maven-shade-plugin** from "**DocUrlsRetriever**" to "**TestNonStandardInputOutput**".
- Inside ***src/test/.../TestNonStandardInputOutput.java***, give the wanted testInput and testOutput files.<br>
- If you want to provide a *.tsv* or a *.csv* file with a title in its column,
    you can specify it in the **util.file.FileUtils.skipFirstRow**-variable, in order for the first-row (headers) to be ignored.
- If you want to see the logging-messages in the *Console*, open the ***resources/logback.xml***
    and change the ***appender-ref***, from ***File*** to ***Console***.<br>
- Run ``mvn install`` to create the new ***JAR*** file.<br>
- Execute the program with the following command:<br>
**``java -jar doc_urls_retriever-0.4-SNAPSHOT.jar arg2:'<dataType: document | dataset | all>' arg3:'-downloadDocFiles' arg4:'-firstDocFileNum' arg5:'NUM' arg6:'-docFilesStorage' arg7:'storageDir' arg8:'-inputDataUrl' arg9: 'inputUrl' arg10: '-numOfThreads' arg11: <NUM>``**
<br><br>
*You can use the argument '-inputFileFullPath' to define the inputFile, instead of the stdin-redirection. That way, the progress percentage will appear in the logging file.*
<br><br>

**Arguments explanation:**
- **-retrieveDataType** and **dataType** will tell the program to retrieve the urls of type "*document*", "*dataset*" or "*all*"-dataTypes.
- **-downloadDocFiles** will tell the program to download the DocFiles.
    The absence of this argument will cause the program to NOT download the docFiles, but just to find the *DocUrls* instead.
    Either way, the DocUrls will be written to the JsonOutputFile.
- **-firstDocFileNum** and **NUM** will tell the program to use numbers as *DocFileNames* and the first *DocFile* will have the given number "*NUM*"
    The absence of this argument-group will cause the program to use the original-docFileNames.
- **-docFilesStorage** and **storageDir** will tell the program to use the given DocFiles-*storageDir*.
    The absence of this argument will cause the program to use a pre-defined storageDir which is: "*./docFiles*".
- **-inputDataUrl** and **inputUrl** will tell the program to use the given *URL* to retrieve the inputFile, instead of having it locally stored and redirect the *Standard Input Stream*.
- **-numOfThreads** and **NUM** will tell the program to use *NUM* number of worker-threads.
<br><br>
  The order of the program's arguments matters only **per pair**. For example, the argument **'storageDir'**, has to be placed always after the **'-docFilesStorage''** argument.
  <br>


## Example
You can check the functionality of **DocUrlsRetriever** by running an example.<br>
Type **`./runExample.sh`** in the terminal and hit `ENTER`.<br>
Then you can see the results in the ***example/sample_output*** directory.<br>
The above script will run the following commands:
- **`mvn clean install`**: Does a *clean install*.
- **`rm -rf example/sample_output/*`**: Removes any previous example-results.
- **``cd target &&
    java -jar doc_urls_retriever-0.4-SNAPSHOT.jar -retrieveDataType document -downloadDocFiles -firstDocFileNum 1 -docFilesStorage ../example/sample_output/DocFiles
    < ../example/sample_input/sample_input.json > ../example/sample_output/sample_output.json``**<br>
    This command will run the program with "**../example/sample_input/sample_input.json**" as input
    and "**../example/sample_output/sample_output.json**" as the output.<br>
    The arguments used are:
    - **-retrieveDataType** and **document** will tell the program to retrieve the urls of type "*document*".
    - **-downloadDocFiles** which will tell the program to download the DocFiles.
    - **-firstDocFileNum 1** which will tell the program to use numbers as DocFileNames and the first DocFile will have the number <*1*>.
    - **-docFilesStorage ../example/sample_output/DocFiles** which will tell the program to use the custom
            DocFilesStorageDir: "*../example/sample_output/DocFiles*".
<br>

## Customizations
- You can set **File-related** customizations in ***util.file.FileUtils.java***.
- You can set **Connection-related** customizations in ***util.url.HttpConnUtils.java*** and ***util.url.ConnSupportUtils.java***.
