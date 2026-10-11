package nocturnal.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    WORLD("World"),
    RENDER("Render"),
    RESEARCH("Research"),
    MISC("Misc");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
