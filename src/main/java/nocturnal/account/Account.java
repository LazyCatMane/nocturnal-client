package nocturnal.account;

public final class Account {
    public enum Type { CRACKED, MICROSOFT }

    public Type type;
    public String name;
    public String uuid;
    /** Microsoft only. Long-lived; exchanged for a fresh Minecraft token on every login. */
    public String refreshToken;

    static Account cracked(String name, String uuid) {
        Account a = new Account();
        a.type = Type.CRACKED;
        a.name = name;
        a.uuid = uuid;
        return a;
    }

    static Account microsoft(MicrosoftAuth.Profile profile) {
        Account a = new Account();
        a.type = Type.MICROSOFT;
        a.name = profile.name();
        a.uuid = profile.uuid().toString();
        a.refreshToken = profile.refreshToken();
        return a;
    }
}
