package sanning;

final class Answer {

    static final Answer EMPTY = new Answer("", 0, "", "", false);

    final String option;
    final int optionNum;
    final String ak;
    final String ts;
    final boolean isOld;

    public Answer(String option, int optionNum, String ak, String ts, boolean isOld) {
        this.option = option;
        this.optionNum = optionNum;
        this.ak = ak;
        this.ts = ts;
        this.isOld = isOld;
    }

    public String toString() { return ts + " " + ak + ":" + optionNum; }

}
