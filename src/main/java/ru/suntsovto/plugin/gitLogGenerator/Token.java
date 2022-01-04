package ru.suntsovto.plugin.gitLogGenerator;


import java.util.HashSet;
import java.util.Set;

import lombok.Getter;

@Getter
public enum Token {
    H("%H"),
    h("%h"),
    an("%an"),
    ad("%ad"),
    s("%s"),
    sWithUrl("%sWithUrl"),
    issueUrl("%issueUrl"),
    gitLabUrl("%gitLabUrl");

    private final String value;
    Token(String value) {
        this.value = value;
    }

    public static Set<String> getAllTokenValue(){
        Set<String> allTokenValue = new HashSet<String>();
        for (Token value : values()) {
            allTokenValue.add(value.getValue());
        }
        return allTokenValue;
    }
}

