package ru.suntsovto.plugin.gitLogGenerator;

import java.io.File;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Формируем текстовый файл по шаблону с коммитами в текущей ветке
 * 
 * @goal generate
 */
public class GitLogGenerator extends AbstractMojo {
    /** @parameter expression="${generate.outputFolder}" default-value="" */
    private String outputFolder;

    /** @parameter expression="${generate.outputFileName}" default-value="CHANGELOG.md" */
    private String outputFileName;

    /**
     * Формат строки вывода
     * %H  - Хеш коммита  
     * %h  - Сокращенный хеш коммита  
     * %an - Имя автора  
     * %ad - Дата автора  
     * %s  - Сообщение
     * 
     * @parameter expression="${generate.template}" default-value="%ad %h %an %s"
     */
    private String template;

    /**
     * Файл шаблона 
     * 
     * Если не задан - не используется. В проекте есть пример файла.
     * 
     * В файле делать цикл по переменной "commits": 
     *   #foreach ($!commit in $!commits)
     *      внутри использовать параметры так же как в строке, но без % (например: commit.an). Исключение полный хэш - Hash
     *   #end
     * 
     * @parameter expression="${generate.templateFile}" default-value=""
     */
    private String templateFile;

    /** @parameter expression="${generate.templateFileUseInternal}" default-value="false" */
    private boolean templateFileUseInternal = false;

    /** @parameter expression="${generate.gitFolder}" default-value="" */
    private String gitFolder;

    /** @parameter expression="${generate.countItems}" default-value="10" */
    private Integer countItems;

    /** @parameter expression="${generate.gitLabUrl}" default-value="" */
    private String gitLabUrl;

    /** @parameter expression="${generate.jiraUrl}" default-value="" */
    private String jiraUrl;
    
    /** @parameter expression="${generate.regExpIssueNumber}" default-value="^(.[^\\s]+)\\s*.*" */
    private String regExpIssueNumber = "^(.[^\\s]+)\\s*.*";
    
    private Pattern pattern;
    private SimpleDateFormat simpleDateFormat;
    
    private void init() {
        if (StringUtils.isNotEmpty(regExpIssueNumber)){
            pattern = Pattern.compile(regExpIssueNumber);
        }
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
    }
    
    @Override
    public void execute() {
        try {
            init();
            
            Repository repo = new FileRepository(gitFolder);
            Git git = new Git(repo);
            
            // тут может быть либо хэш, если чекаут по коммиту (дженкинс), либо имя вестки, если чекаут бранча
            String fullBranch = repo.getFullBranch();
            
            // вытаскиваем имя ветки/веток по FullBranch
            Set<String> currentBranches = getCurrentBranches(repo, fullBranch);

            ObjectId resolve = repo.resolve(fullBranch);
            Iterable<RevCommit> logs = git.log().add(resolve).setMaxCount(countItems).call();

            // наполнение мапы информацией о коммитах
            List<Map<String, String>> commitMapList = new ArrayList<Map<String, String>>();
            for (RevCommit commit : logs) {
                commitMapList.add(getCommitInfoMap(commit));
            }

            StringBuffer commitLines = new StringBuffer();
            if ((templateFile == null || templateFile.isEmpty()) && !templateFileUseInternal){
                // файл на основе строки из конфига
                commitLines.append("Current branch: ").append(currentBranches).append("\n");
                byTemplateStr(commitMapList, commitLines);
            } else {
                // файл на основе файла шаблона
                 byTemplateFile(currentBranches, commitMapList, commitLines);
            }
            
            if (commitLines.length()>0){
                File file = new File(outputFolder + File.separator + outputFileName);
                FileUtils.writeStringToFile(file, commitLines.toString(), "UTF_8");
            }
        } catch (Exception e) {
            System.out.println("Error get commits: " + e.getMessage());
        }
    }

    /** Create lines with commit by template file */
    private void byTemplateFile(Set<String> currentBranches, List<Map<String, String>> commitMapList, StringBuffer commitLines) throws Exception {
        VelocityContext context = getVelocityContext();
        context.put("commits", convertToDto(commitMapList));
        context.put("currentBranch", currentBranches.toString());
        StringWriter writer = new StringWriter();

        if (templateFileUseInternal){
            InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("templateHtmlTable.vm");
            String templateInternal = IOUtils.toString(resourceAsStream, "UTF_8");
            Velocity.evaluate(context, writer, "", templateInternal);
        } else {
            Template template = Velocity.getTemplate(templateFile, "utf-8");
            template.merge(context, writer);
        }

        commitLines.append(writer.getBuffer());
    }

    /** Create lines with commit by template string from plugin config */
    private void byTemplateStr(List<Map<String, String>> commitMapList, StringBuffer commitLines) {
        for (Map<String, String> tokens : commitMapList) {
            StringBuffer line = new StringBuffer();
            String patternString = "(" + StringUtils.join(tokens.keySet(), "|") + ")";
            Pattern pattern1 = Pattern.compile(patternString);
            Matcher matcher = pattern1.matcher(template);

            while(matcher.find()) {
                matcher.appendReplacement(line, tokens.get(matcher.group(1)));
            }
            matcher.appendTail(line);
            
            commitLines.append(line).append("\n");
        }
    }

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
        return currentBranches;
    }

    private List<CommitDto> convertToDto(List<Map<String, String>> commitMapList) {
        List<CommitDto> commitDtoList = new ArrayList<CommitDto>();
        if (commitMapList == null || commitMapList.isEmpty()){
            return commitDtoList;
        }
        for (Map<String, String> commitMap : commitMapList) {
            CommitDto commitDto = new CommitDto();
            commitDto.setAd(commitMap.get("%ad"));
            commitDto.setAn(commitMap.get("%an"));
            commitDto.setH(commitMap.get("%h"));
            commitDto.setHash(commitMap.get("%H"));
            commitDto.setS(commitMap.get("%s"));
            commitDto.setGitLabUrl(getGitLabUrl(commitMap.get("%H")));
            commitDto.setSWithUrl(getReplacedIssueNumberToJiraUrl(commitMap.get("%s")));
            commitDtoList.add(commitDto);
        }
        return commitDtoList;
    }

    private String getGitLabUrl(String h) {
        return addEndSlash(gitLabUrl) + h;
    }

    /** Extract commit info to map by key */
    private Map<String, String> getCommitInfoMap(RevCommit commit) {
        Map<String,String> tokens = new HashMap<String,String>();
        tokens.put("%H", commit.getName());
        tokens.put("%h", commit.abbreviate(8).name());
        tokens.put("%an", commit.getAuthorIdent().getName());
        tokens.put("%ad", simpleDateFormat.format(new Date(commit.getCommitTime() * 1000L)));
        String value = commit.getShortMessage().replace("$", "\\$");
        tokens.put("%s", value);
        return tokens;
    }

    /** Replace jura issue number in commit message */
    private String getReplacedIssueNumberToJiraUrl(String value) {
        if (StringUtils.isEmpty(value) || StringUtils.isEmpty(regExpIssueNumber) || StringUtils.isEmpty(jiraUrl)){
            return value;
        }
        
        String tagATemplate = "<a href=\"" + addEndSlash(jiraUrl) + "%s\">%s</a>";
        
        try {
            Matcher matcher = pattern.matcher(value);
            StringBuffer line = new StringBuffer();
            while(matcher.find()) {
                String group = matcher.group(1);
                String tagA = String.format(tagATemplate, group, group);
                matcher.appendReplacement(line, tagA);
            }
            matcher.appendTail(line);
            return line.toString();
        } catch (Exception ex){
            System.out.println("Error create jira url: " + ex.getMessage());
        }
        
        return value;
    }

    /** Add slash to end is need */
    private String addEndSlash(String url){
        if (StringUtils.isEmpty(url)){
            return url;
        }
        return url.endsWith("/") ? url : url+"/";
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
