package ru.suntsovto.plugin.gitLogGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Create text/html/json file with commits
 * Parameter add to maven environment - git.branch, git.commit.hash, git.commit.date
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GitLogGenerator extends AbstractMojo {
    
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    
    /** 
     * Folder .git for extract info
     * 
     * Default value: search in the root directory of the project, then in the parent directory and above.
     */
    @Parameter(property = "gitFolder")
    private String gitFolder;
    
    /** 
     * Output folder for generated file
     * 
     * Default value: target/generated-resources/resources"
     */
    @Parameter(property = "outputFolder", defaultValue = "target/generated-resources/resources")
    private String outputFolder;

    /** 
     * File name for generated file without file extension. File extension from parameter "format"
     * 
     * Default value: CHANGELOG
     */
    @Parameter(property = "outputFileName", defaultValue = "CHANGELOG")
    private String outputFileName;

    /** 
     * Format output file
     *    json - json file with commit param
     *    html - html file by template from param "templateFile". Plugin exist internal html template file - see param "useInternalTemplate"
     *    txt  - txt file, one commit = one line in txt file, line format from param "templateLine" 
     *    other - process as txt, param value used as file extension
     * 
     * Can specify multiple values separated by commas
     * 
     * Default value: txt
     */
    @Parameter(property = "formats", defaultValue = "txt")
    private String formats;
    
    /** 
     * Count commit write to file 
     * 
     * Default value: 10
     */
    @Parameter(property = "countCommits", defaultValue = "10")
    private Integer countCommits;

    // ------ FOR TXT FILE ---------
    /**
     * Format single line for param "outputFileFormat" = txt
     * %H  - full commit hash  
     * %h  - brief commit hash  
     * %an - author name  
     * %ad - author date  
     * %s  - message
     * 
     * Default value: %ad %h %an %s
     * Example: 2021-03-08 14:31:52 7d0ab294 tsuntsov JIRA-123 simples by picture
     */
    @Parameter(property = "templateLine", defaultValue = "%ad %h %an %s")
    private String templateLine;

    /**
     * Add current branch first line in txt file
     *
     * Default value: true
     * Example: Branch: [main]
     */
    @Parameter(property = "addCurrentBranchFirstLine", defaultValue = "true")
    private String addCurrentBranchFirstLine;

    // ------ FOR HTML FILE ---------
    /** 
     * Use internal html template. Template contains html table with commits
     * Add param "gitLabUrl" and "bugTrackingUrl" for create tag &lt;a&gt; in table.
     * Use param "internalTemplateOnlyTable" for add &lt;html&gt; tag
     *
     * Default value: true
     */
    @Parameter(property = "useInternalTemplate", defaultValue = "true")
    private boolean useInternalTemplate = true;
    
    /** 
     * Use internal html template. Template contains html table with commits
     * Add param "gitLabUrl" and "bugTrackingUrl" for create tag &lt;a&gt; in table.
     *
     * Default value: true
     * Example: true  - file start from &lt;table&gt;
     *          false - file start from &lt;html&gt;
     */
    @Parameter(property = "internalTemplateOnlyTable", defaultValue = "true")
    private boolean internalTemplateOnlyTable = false;
    
    /**
     * Url git lab for add link in table commit (by hash)
     * Add tag &lt;a href=gitLabUrl/commitHash&gt;commitHash&lt;a/&gt;
     * Used is outputFileFormat=html/json
     * 
     * Default value: value extract from .git
     */
    @Parameter(property = "gitLabUrl")
    private String gitLabUrl;

    /**
     * Url jira for add link in table commit (by message)
     * Add tag &lt;a href=bugTrackingUrl/issueNumber&gt;issueNumber&lt;a/&gt;
     * issueNumber - extract from commit message by parameter "regExpIssueNumber"
     * 
     * Used is outputFileFormat=html/json
     * 
     * Default value: not specified
     */
    @Parameter(property = "bugTrackingUrl")
    private String bugTrackingUrl;
    
    /**
     * Regexp for extract issue number from commit message, used together with the parameter "bugTrackingUrl"
     * By default extract letter and digit from start commit message
     * 
     * Used is outputFileFormat=html/json
     * 
     * Default value: ^(.[^\s]+)\s*.*
     * Example: "JIRA-ISSUE-123 bag fix message". "JIRA-ISSUE-123" issue number by default regexp
     */
    @Parameter(property = "regExpIssueNumber", defaultValue = "^(.[^\\s]+)\\s*.*")
    private String regExpIssueNumber = "^(.[^\\s]+)\\s*.*";

    /** 
     * Silent mode with error. 
     * true - error write build log, false - throw exception 
     * 
     * Default value: true
     */
    @Parameter(property = "silent", defaultValue = "true")
    private boolean silent;
    
    /** 
     * Trace to log process plugin
     * 
     * Default value: false
     */
    @Parameter(property = "trace", defaultValue = "false")
    private boolean trace;

    /**
     * Template file to html output file
     * todo
     * Если не задан - не используется. В проекте есть пример файла.
     *
     * В файле делать цикл по переменной "commits": 
     *   #foreach ($!commit in $!commits)
     *      внутри использовать параметры так же как в строке, но без % (например: commit.an). Исключение полный хэш - Hash
     *   #end
     *
     */
    @Parameter(property = "templateFile", defaultValue = "")
    private String templateFile;
    
    private Pattern pattern;
    private SimpleDateFormat simpleDateFormat;
    
    private void init() throws IOException {
        if (StringUtils.isNotEmpty(regExpIssueNumber)){
            pattern = Pattern.compile(regExpIssueNumber);
        }
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
        
        validateConfig();
    }

    private void validateConfig() throws IOException {
        // init git folder
        if (StringUtils.isEmpty(gitFolder)) {
            if (new File(".git").exists()){
                gitFolder = ".git";
            } else if (new File("../.git").exists()){
                gitFolder = "../.git";
            } else if (new File("../../.git").exists()){
                gitFolder = "../../.git";
            }
            trace("found git filder: " + gitFolder);
        }
        
        if (StringUtils.isEmpty(gitLabUrl)){
            String urlFromRepo = new FileRepository(gitFolder).getConfig().getString("remote", "origin", "url");
            trace("origin: " + urlFromRepo);
            gitLabUrl = urlFromRepo + "/commit/";
        }
    }
    
    @Override
    public void execute() {
        try {
            init();
            
            // main repo for extract info
            Repository repo = new FileRepository(gitFolder);
            
            // this hash (checkout by commit) or branch name (checkout branch)
            String fullBranch = repo.getFullBranch();
            // extract cut branch name from FullBranch
            Set<String> currentBranches = getCurrentBranches(repo, fullBranch);
            // add to maven environment parameter
            addPropertyEnv("git.branch", currentBranches.toString());
            
            // get commit list
            Iterable<RevCommit> logs = getCommitsFromRepo(repo, fullBranch);
            // commit info to dto
            List<CommitDto> commitDtoList = createCommitDtoList(logs);

            for (String format : formats.split(",")) {
                String commitLines = createCommitLines(currentBranches, commitDtoList, format);
                File file = getOutputFile(format);
                commitLinesToFile(commitLines, file);
            }
        } catch (Exception e) {
            error("Error get commits: " + e.getMessage());
        }
    }

    private void commitLinesToFile(String commitLines, File file) throws IOException {
        if (StringUtils.isNotEmpty(commitLines)){
            FileUtils.writeStringToFile(file, commitLines, "utf-8");
            trace("Output file created");
        }
    }

    private String createCommitLines(Set<String> currentBranches, List<CommitDto> commitDtoList, String format) throws Exception {
        String commitLines;
        if (FormatEnum.JSON.is(format)){
            // json file by internal dto
            commitLines = createJson(currentBranches, commitDtoList);
        } else if (FormatEnum.HTML.is(format)){
            // html file by template (user or internal)
            commitLines = createHtml(currentBranches, commitDtoList);
        } else {
            // other - txt file by templateLine 
            commitLines = createTxt(currentBranches, templateLine, commitDtoList);
        }
        trace(" *** Result *** \n" + commitLines);
        return commitLines;
    }

    private File getOutputFile(String format) {
        File file = new File(outputFolder + File.separator + outputFileName + "." + format.trim());
        trace("git log write to file " + file.getAbsolutePath());

        if (file.exists()){
            trace("Output file already exists, try delete");
            boolean delete = file.delete();
            if (!delete){
                error("Output file already exists, delete error. Process terminated");
            }
        }
        
        return file;
    }

    private List<CommitDto> createCommitDtoList(Iterable<RevCommit> logs) {
        List<CommitDto> commitDtoList = new ArrayList<CommitDto>();
        boolean isFirst = true;
        for (RevCommit commit : logs) {
            CommitDto commitDto = getCommitDto(commit);
            if (isFirst){
                // add to maven environment parameter with head commit
                addPropertyEnv("git.commit.hash", commitDto.getH());
                addPropertyEnv("git.commit.date", commitDto.getAd());
                isFirst = false;
            }
            commitDtoList.add(commitDto);
            trace(commitDto.toString());
        }
        return commitDtoList;
    }

    /** Create JSON string */
    protected String createJson(Set<String> currentBranches, List<CommitDto> commitDtoList) {
        trace("create JSON");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        CommitsJson jsonDto = new CommitsJson();
        jsonDto.setBranches(currentBranches);
        jsonDto.setCommits(commitDtoList);
        
        return gson.toJson(jsonDto);
    }

    /** Add property to maven environment */
    private void addPropertyEnv(String name, String value) {
        project.getProperties().put(name, value);
        trace("add property: " + name + " = " + value);
    }

    private CommitDto getCommitDto(RevCommit commit) {
        CommitDto dto = new CommitDto();
        dto.setHash(commit.getName());
        dto.setH(commit.abbreviate(8).name());
        dto.setAn(commit.getAuthorIdent().getName());
        dto.setAd(simpleDateFormat.format(new Date(commit.getCommitTime() * 1000L)));
        String value = commit.getShortMessage().replace("$", "\\$");
        dto.setS(value);
        dto.setGitLabUrl(getGitLabUrl(commit.getName()));
        dto.setIssueUrl(getIssueUrl(value));
        dto.setSWithUrl(getReplacedIssueNumberToUrl(value));
        return dto;
    }

    private Iterable<RevCommit> getCommitsFromRepo(Repository repo, String fullBranch) throws IOException, GitAPIException {
        Git git = new Git(repo);
        ObjectId resolve = repo.resolve(fullBranch);
        LogCommand log = git.log().add(resolve).setMaxCount(countCommits);
        return log.call();
    }

    /** Create lines with commit by template file */
    protected String createHtml(Set<String> currentBranches, List<CommitDto> commitDtoList) throws Exception {
        trace("create HTML");
        VelocityContext context = getVelocityContext();
        context.put("commits", commitDtoList);
        context.put("currentBranch", currentBranches.toString());
        StringWriter writer = new StringWriter();

        if (useInternalTemplate){
            String templateName = internalTemplateOnlyTable ? "templateHtmlTable.vm" : "templateHtml.vm";
            InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(templateName);
            if (resourceAsStream == null){
                error("templateHtmlTable.vm not found");
                return null;
            }
            String templateInternal = IOUtils.toString(resourceAsStream, "utf-8");
            Velocity.evaluate(context, writer, "", templateInternal);
        } else {
            Template template = Velocity.getTemplate(templateFile, "utf-8");
            template.merge(context, writer);
        }

        return writer.toString();
    }

    /** Create lines with commit by template string from plugin config */
    protected  String createTxt(Set<String> currentBranches, String templateLine, List<CommitDto> commitDtoList) {
        trace("create TXT");
        // create pattern for replace 
        Set<String> tokens = Token.getAllTokenValue();
        String patternString = "(" + StringUtils.join(tokens, "|") + ")";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(templateLine);

        // create value map
        List<Map<String, String>> commitMapList = new ArrayList<Map<String, String>>();
        for (CommitDto dto : commitDtoList) {
            commitMapList.add(getCommitInfoMap(dto));
        }
        
        // add Current branch as first line
        StringBuilder commitLines = new StringBuilder();
        commitLines.append("Branch: ").append(currentBranches).append("\n");
        for (Map<String, String> tokenToValueMap : commitMapList) {
            StringBuffer line = new StringBuffer();
            while(matcher.find()) {
                matcher.appendReplacement(line, tokenToValueMap.get(matcher.group(1)));
            }
            matcher.appendTail(line);
            commitLines.append(line).append("\n");
            matcher.reset();
        }
        
        return commitLines.toString();
    }

    /** Magic code... I don't remember the reasons */
    private Set<String> getCurrentBranches(Repository repo, String fullBranch) {
        Set<String> currentBranches = new HashSet<String>();
        for (Map.Entry<String, Ref> entry : repo.getAllRefs().entrySet()) {
            String hash = entry.getValue().getObjectId().getName();
            if (fullBranch.equals(entry.getKey()) || fullBranch.equals(hash)){
                String branch = entry.getKey();
                branch = branch.replace("refs/", "").replace("remotes/", "").replace("origin/", "").replace("heads/", "");
                if (!branch.equals("HEAD")){
                    currentBranches.add(branch);
                }
            }
        }
        trace("currentBranches: " + currentBranches);
        return currentBranches;
    }

    private String getGitLabUrl(String h) {
        return addEndSlash(gitLabUrl) + h;
    }

    /** Extract commit info to map by key */
    private Map<String, String> getCommitInfoMap(CommitDto dto) {
        Map<String,String> tokens = new HashMap<String,String>();
        tokens.put(Token.H.getValue(), dto.getHash());
        tokens.put(Token.h.getValue(), dto.getH());
        tokens.put(Token.an.getValue(), dto.getAn());
        tokens.put(Token.ad.getValue(), dto.getAd());
        tokens.put(Token.s.getValue(), dto.getS());
        tokens.put(Token.gitLabUrl.getValue(), dto.getGitLabUrl());
        tokens.put(Token.sWithUrl.getValue(), dto.getSWithUrl());
        tokens.put(Token.issueUrl.getValue(), dto.getIssueUrl());
        return tokens;
    }

    /** Create issue URL by issue number from commit message and "bugTrackingUrl" */
    public String getIssueUrl(String commitMessage){
        if (StringUtils.isEmpty(commitMessage) || StringUtils.isEmpty(regExpIssueNumber) || StringUtils.isEmpty(bugTrackingUrl)){
            return "";
        }

        try {
            Matcher matcher = pattern.matcher(commitMessage);
            if (matcher.find()) {
                String issueNumber = matcher.group(1);
                return  addEndSlash(bugTrackingUrl) + issueNumber;
            }
        } catch (Exception ex){
            error("Error create jira url: " + ex.getMessage());
        }

        return "";
    }

    /** Replace issue number in commit message */
    private String getReplacedIssueNumberToUrl(String commitMessage) {
        if (StringUtils.isEmpty(commitMessage) || StringUtils.isEmpty(regExpIssueNumber) || StringUtils.isEmpty(bugTrackingUrl)){
            return commitMessage;
        }
        
        String tagATemplate = "<a href=\"" + addEndSlash(bugTrackingUrl) + "%s\">%s</a>";
        
        try {
            Matcher matcher = pattern.matcher(commitMessage);
            String commitMessageWithTag = commitMessage;
            while(matcher.find()) {
                String group = matcher.group(1);
                String tagA = String.format(tagATemplate, group, group);
                commitMessageWithTag = commitMessageWithTag.replaceAll(group, tagA);
            }
            return commitMessageWithTag;
        } catch (Exception ex){
            error("Error create jira url: " + ex.getMessage());
        }
        
        return commitMessage;
    }

    /** Add slash to end is need */
    private String addEndSlash(String url){
        if (StringUtils.isEmpty(url)){
            return "";
        }
        return url.endsWith("/") ? url : url + "/";
    }
    
    private void error(String message) {
        if (silent){
            System.err.println(message);
        } else {
            throw new RuntimeException("gitlog-plugin: " + message);
        }
    }

    private void trace(String str) {
        if (trace){
            System.out.println(str);
        }
    }

    public static VelocityContext getVelocityContext() throws Exception {
        VelocityContext velocityContext = new VelocityContext();
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("velocity.properties");
        Properties velocityProperties = new Properties();
        velocityProperties.load(is);
        velocityProperties.setProperty("runtime.log.logsystem.log4j.logger", "stdout");
        Velocity.init(velocityProperties);
        return velocityContext;
    }
}
