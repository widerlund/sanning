package sanning;

final class Answer {

    static final Answer EMPTY = new Answer("", "", "", "", false);

    final String ts;
    final String ak;
    final String po;
    final String o;
    final boolean isOld;

    public Answer(String ts, String ak, String po, String o, boolean isOld) {
        this.ts = ts;
        this.ak = ak;
        this.po = po;
        this.o = o;
        this.isOld = isOld;
    }

    public String toString() { return ts + " " + ak + ":" + po; }

}
