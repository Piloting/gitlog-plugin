# Plugin for generate file with commit

Create text/html/json file with commits. Maven environment fill param git.branch, git.commit.hash, git.commit.date

### Min config: ###

    <plugin>
        <groupId>ru.suntsovto.plugin</groupId>
        <artifactId>gitlog-plugin</artifactId>
        <version>1.0.2</version>
        <executions>
            <execution>
                <phase>generate-resources</phase>
                <goals><goal>generate</goal></goals>
            </execution>
        </executions>
    </plugin>

### Add to target file CHANGELOG.txt: ###

    Branch: [main]
    2021-03-08 14:31:52 7d0ab294 tsuntsov simples by picture
    2021-03-08 09:35:18 c741dd94 tsuntsov remove spring, work fx
    2021-03-01 18:00:00 b2c23ffc tsuntsov javafx. simples form
    2021-02-27 19:05:22 8a3e0cd1 tsuntsov change project structure
    2021-02-27 18:55:13 6bd2db1a tsuntsov impl controller
    2021-02-26 14:46:59 464493a6 tsuntsov controller prepare
    2021-02-25 14:12:27 da72e7a3 tsuntsov add ChessStructure, WindowStructure
    2021-02-24 20:46:20 1303e388 tsuntsov add gen struct - rnd, line
    2021-02-24 13:06:31 b82aa81c Piloting Update maven.yml
    2021-02-24 12:49:38 2682164e Piloting Create maven.yml

See example folder for json and html file

### Parameters: ###

**gitFolder**\
Folder .git for extract info\
*Default value:* search in the root directory of the project, then in the parent directory and above.

**outputFolder**\
Output folder for generated file\
*Default value:* target/generated-resources/resources"

**outputFileName**\
File name for generated file without file extension. File extension from parameter "format"\
*Default value:* CHANGELOG

**formats**\
Format output file
   * json - json file with commit param
   * html - html file by template from param "templateFile". Plugin exist internal html template file - see param "useInternalTemplate"
   * txt  - txt file, one commit = one line in txt file, line format from param "templateLine"
   * other - process as txt, param value used as file extension\
Can specify multiple values separated by commas\
*Default value:* txt

**countCommits**\
Count commit write to file\
*Default value:* 10

**templateLine**\
Format single line for param "outputFileFormat" = txt
* %H  - full commit hash  
* %h  - brief commit hash  
* %an - author name  
* %ad - author date  
* %s  - message\
*Default value:* %ad %h %an %s\
*Example:* 2021-03-08 14:31:52 7d0ab294 tsuntsov JIRA-123 simples by picture

**addCurrentBranchFirstLine**\
Add current branch first line in txt file\
*Default value:* true\
*Example:* Branch: [main]

**useInternalTemplate**\
Use internal html template. Template contains html table with commits\
Add param "gitLabUrl" and "bugTrackingUrl" for create tag &lt;a&gt; in table.\
Use param "internalTemplateOnlyTable" for add &lt;html&gt; tag\
*Default value:* true

**internalTemplateOnlyTable**\
Use internal html template. Template contains html table with commits
Add param "gitLabUrl" and "bugTrackingUrl" for create tag &lt;a&gt; in table.\
*Default value:* true\
*Example:*
* true  - file start from &lt;table&gt;
* false - file start from &lt;html&gt;

**gitLabUrl**\
Url git lab for add link in table commit (by hash)\
Add tag &lt;a href=gitLabUrl/commitHash&gt;commitHash&lt;a/&gt;\
Used is outputFileFormat=html/json\
*Default value:* value extract from .git

**bugTrackingUrl**\
Url jira for add link in table commit (by message)\
Add tag &lt;a href=bugTrackingUrl/issueNumber&gt;issueNumber&lt;a/&gt;\
issueNumber - extract from commit message by parameter "regExpIssueNumber"\
Used is outputFileFormat=html/json\
*Default value:* not specified

**regExpIssueNumber**\
Regexp for extract issue number from commit message, used together with the parameter "bugTrackingUrl"\
By default extract letter and digit from start commit message\
Used is outputFileFormat=html/json\
*Default value:* ^(.[^\s]+)\s*.*\
*Example:* "JIRA-ISSUE-123 bag fix message". "JIRA-ISSUE-123" issue number by default regexp

**silent**\
Silent mode with error.\
true - error write build log, false - throw exception\
*Default value:* true

**trace**\
Trace to log process plugin\
*Default value:* false
