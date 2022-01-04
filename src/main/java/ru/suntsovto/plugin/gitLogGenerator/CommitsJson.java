package ru.suntsovto.plugin.gitLogGenerator;

import java.util.List;
import java.util.Set;

import lombok.Data;

@Data
public class CommitsJson {
    private Set<String> branches;
    private List<CommitDto> commits;
}
