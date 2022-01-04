package ru.suntsovto.plugin.gitLogGenerator;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * %H  Hash (full)
 * %h  Hash (cut)
 * %an Author name
 * %ad Author date
 * %s  Message (head)
 */
@Getter
@Setter
@ToString
public class CommitDto {
    private String Hash;
    private String h;
    private String an;
    private String ad;
    private String s;
    private String sWithUrl;
    private String gitLabUrl;
    private String issueUrl;
}
