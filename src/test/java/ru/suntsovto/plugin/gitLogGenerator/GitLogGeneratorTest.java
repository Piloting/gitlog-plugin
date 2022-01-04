package ru.suntsovto.plugin.gitLogGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class GitLogGeneratorTest {
    
    @Test
    public void createTxtTest(){
        GitLogGenerator gen = new GitLogGenerator();

        List<CommitDto> list = new ArrayList<CommitDto>();
        list.add(createCommitDto("JIRA-123 hello"));
        list.add(createCommitDto("JIRA-123 hello2"));
        String templateLine = "%ad - %H - %h - %an: %s (%issueUrl). %gitLabUrl";

        Set<String> currentBranches = Collections.singleton("master");
        
        String sb = gen.createTxt(currentBranches, templateLine, list);
        String expected1 = "2000.01.01 00:00:00 - 123123123123123123 - 123123 - suntsovto: JIRA-123 hello (jiraUrl/JIRA-123). gitLabUrl";
        String expected2 = "2000.01.01 00:00:00 - 123123123123123123 - 123123 - suntsovto: JIRA-123 hello2 (jiraUrl/JIRA-123). gitLabUrl";
        Assert.assertTrue(sb.contains(expected1));
        Assert.assertTrue(sb.contains(expected2));
    }

    @Test
    public void createJsonTest(){
        GitLogGenerator gen = new GitLogGenerator();

        List<CommitDto> commitDtoList = new ArrayList<CommitDto>();
        commitDtoList.add(createCommitDto("JIRA-123 hello"));
        commitDtoList.add(createCommitDto("JIRA-123 hello2"));

        Set<String> currentBranches = Collections.singleton("master");
        
        String sb = gen.createJson(currentBranches, commitDtoList); 
        Assert.assertTrue(sb.contains("\"s\": \"JIRA-123 hello\""));
        Assert.assertTrue(sb.contains("\"s\": \"JIRA-123 hello2\""));
    }
    
    @Test
    public void createHtmlTest() throws Exception {
        GitLogGenerator gen = new GitLogGenerator();

        List<CommitDto> commitDtoList = new ArrayList<CommitDto>();
        commitDtoList.add(createCommitDto("JIRA-123 hello"));
        commitDtoList.add(createCommitDto("JIRA-123 hello2"));

        Set<String> currentBranches = Collections.singleton("master");
        
        String sb = gen.createHtml(currentBranches, commitDtoList);
        Assert.assertTrue(sb.contains("<td>jiraUrl/JIRA-123 hello</td>"));
        Assert.assertTrue(sb.contains("<td>jiraUrl/JIRA-123 hello2</td>"));
    }
    
    private CommitDto createCommitDto(String message) {
        CommitDto commitDto = new CommitDto();
        commitDto.setHash("123123123123123123");
        commitDto.setH("123123");
        commitDto.setAd("2000.01.01 00:00:00");
        commitDto.setAn("suntsovto");
        commitDto.setIssueUrl("jiraUrl/JIRA-123");
        commitDto.setSWithUrl("jiraUrl/" + message);
        commitDto.setGitLabUrl("gitLabUrl");
        commitDto.setS(message);
        return commitDto;
    }

}
