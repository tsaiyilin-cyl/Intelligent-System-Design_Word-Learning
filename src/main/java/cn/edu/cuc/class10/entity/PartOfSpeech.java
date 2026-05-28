package cn.edu.cuc.class10.entity;

public enum PartOfSpeech {
    NOUN("名词"),
    VERB("动词"),
    ADJECTIVE("形容词"),
    ADVERB("副词"),
    PREPOSITION("介词"),
    CONJUNCTION("连词"),
    PRONOUN("代词"),
    INTERJECTION("感叹词"),
    ARTICLE("冠词"),
    INDEFINITE_ARTICLE("不定冠词"),
    ABBREVIATION("缩写"),
    AUXILIARY("助动词"),
    NUMERAL("数词"),
    MODAL_VERB("情态动词");

    private final String displayName;

    PartOfSpeech(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
