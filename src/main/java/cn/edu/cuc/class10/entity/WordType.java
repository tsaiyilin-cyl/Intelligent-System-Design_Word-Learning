package cn.edu.cuc.class10.entity;

public enum WordType {
    SYLLABUS("考纲词汇"),
    CUSTOM("用户自建");

    private final String displayName;

    WordType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
