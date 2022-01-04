package ru.suntsovto.plugin.gitLogGenerator;

public enum FormatEnum {
    TXT,
    JSON,
    HTML;
    
    public boolean is(String value){
        return value != null && value.trim().equalsIgnoreCase(name());
    } 
}
