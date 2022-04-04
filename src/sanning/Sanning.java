package sanning;

import static sanning.Util.hash;
import static sanning.Util.toBase64;
import static sanning.Util.toHex;
import static sanning.Util.toISO8601;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Sanning {

    static final int AK_LEN = toBase64(hash("")).length(); // Length of anonymous key (base 64 encoded SHA-256 hash).
    static final int PO_LEN = toBase64(hash("")).length(); // Length of protected option (base 64 encoded SHA-256 hash).

    final String name;

    File file;
    String title;
    String text;
    String[] options;
    StringBuilder answers;
    int[] summary;
    String seal;

    Sanning(String name, String storageDir) throws IOException {
        this.name = (name.endsWith(".txt") ? name.substring(0, name.length() - 4) : name);

        this.file = new File(storageDir, this.name + ".txt");
        BufferedReader in = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));

        title = in.readLine();
        text = readText(in);
        options = readOptions(in);
        String summaryLine = in.readLine();
        in.readLine();
        if (summaryLine != null) {
            String[] summaryStr = summaryLine.split(":");
            summary = new int[summaryStr.length];
            for (int ix = 0; ix < summary.length; ix++) {
                summary[ix] = Integer.parseInt(summaryStr[ix]);
            }
        } else {
            summary = new int[options.length];
        }
        answers = readAnswers(in);
        seal = in.readLine();

        // Verify seal.
        String actualSeal = genSeal();
        if (seal != null && !seal.equals(actualSeal)) {
            throw new IllegalStateException("seal broken: " + seal);
        }
    }

    /**
     * Answer sanning with one option.
     * @param ik        identity key
     * @param optionNum answer option number
     * @param p         personal code
     * @return timestamped answer
     */
    synchronized Answer doAnswer(String ik, int optionNum, String p) throws IOException {
        if (optionNum >= summary.length) {
            throw new IllegalArgumentException("invalid answer: " + optionNum);
        }
        String o = options[optionNum];

        // Generate AK.
        String ak = generateAK(ik);

        // Generate PO.
        String po = generatePO(ak, p, o);

        // Verify that AK has not answered.
        Answer oldAnswer = lookupAnswer(ak, p);
        if (oldAnswer != null) {
            return oldAnswer;
        }

        // Update summary.
        summary[optionNum]++;

        // Timestamp.
        String ts = toISO8601(System.currentTimeMillis());

        // Create new answer line.
        answers.append(ts).append(" ").append(ak).append(":").append(po).append('\n');

        // Persist (always for now!).
        persist();

        return new Answer(ts, ak, po, o, false);
    }

    String persist() throws IOException {
        // Write output file.
        PrintWriter out = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8));
        String s = toString();
        out.print(s);
        out.close();
        return s;
    }

    /**
     * Lookup answer line for ak.
     * @return answer line or null if not found
     */
    Answer lookupAnswer(String ak, String p) {
        int ix1 = (answers != null) ? answers.indexOf(" " + ak + ":") : -1;
        if (ix1 != -1) {
            int ix2 = answers.indexOf("\n", ix1);
            String answerLine = answers.substring(ix1 - Util.ISO_8601_LEN, ix2);

            // <ts> <ak>:<po>
            ix1 = answerLine.indexOf(' ');
            String ts = answerLine.substring(0, ix1);
            ix2 = answerLine.indexOf(':', ix1);

            String po = answerLine.substring(ix2 + 1);
            String option = null;
            if (p != null) {
                // Use P to reveal actual option.
                for (String o : options) {
                    if (generatePO(ak, p, o).equals(po)) {
                        option = o;
                        break;
                    }
                }
            }
            return new Answer(ts, ak, po, option, true);
        } else {
            return null;
        }
    }


    /** Timestamp of last answer. */
    synchronized String lastTS() {
        String lastAnswerLine = answers.substring(Math.max(0, answers.length() - (Util.ISO_8601_LEN + 1 + Sanning.AK_LEN + 1 + Sanning.PO_LEN + 1)));
        int ix = lastAnswerLine.indexOf('\n') + 1;
        if (ix >= lastAnswerLine.length()) {
            ix = 0;
        }
        return lastAnswerLine.isBlank() ? lastAnswerLine : lastAnswerLine.substring(ix, lastAnswerLine.indexOf(' '));
    }

    //
    // Helper methods:
    //

    /**
     * Generate anonymous key, AK.
     * @param ik identity key
     */
    String generateAK(String ik) {
        byte[] akBytes = hash(text, ik);
        return toBase64(akBytes);
    }

    /**
     * Generate protected option, PO.
     * @param ak anonymous key
     * @param p personal code
     * @param o option
     * @return
     */
    String generatePO(String ak, String p, String o) {
        byte[] poBytes = hash(ak, p, o);
        return toBase64(poBytes);
    }

    /**
     * Read text lines up until and not including blank line.
     * @return read text or null if in is EOS
     */
    String readText(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals("")) {
                break;
            } else {
                sb.append(line).append('\n');
            }
        }
        String text = sb.toString();
        return text.isEmpty() ? null : text.substring(0, text.length() - 1);
    }

    /**
     * Read option lines up until and not including blank line.
     * @return read options or null if in is EOS
     */
    String[] readOptions(BufferedReader in) throws IOException {
        List<String> optionList = new ArrayList<>();
        String option;
        while ((option = in.readLine()) != null) {
            if (option.equals("")) {
                break;
            } else {
                optionList.add(option);
            }
        }
        return optionList.isEmpty() ? null : optionList.toArray(new String[0]);
    }

    /**
     * Read text lines up until and not including blank line.
     * Answers are verified to not contain key duplicates.
     * @return read answers or null if in is EOS
     */
    StringBuilder readAnswers(BufferedReader in) throws IOException, IllegalStateException {
        Set<String> keySet = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals("")) {
                break;
            } else {
                String ak = line.substring(Util.ISO_8601_LEN + 1, Util.ISO_8601_LEN + 1 + Sanning.AK_LEN);
                if (!keySet.add(ak)) {
                    throw new IllegalStateException("duplicate anonymous key: " + ak);
                }
                sb.append(line).append('\n');
            }
        }
        return sb;
    }

    /**
     * Generate seal for complete sanning.
     * @return seal as hex string
     */
    String genSeal() {
        // TODO: Use system private key to generate seal.
        //       System public key can then be used for seal verification.
        //       Simple hash for now.

        return toHex(hash(title, text, String.join("", options), String.join("", answers)));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Title.
        sb.append(title).append('\n');

        // Text.
        sb.append(text).append("\n\n");

        // Options.
        for (String s : options) {
            sb.append(s).append('\n');
        }
        sb.append('\n');

        // Summary.
        for (int ix = 0; ix < summary.length; ix++) {
            sb.append(summary[ix]);
            if (ix + 1 < summary.length) {
                sb.append(":");
            }
        }
        sb.append("\n\n");

        // Answers.
        if (answers.length() > 0) {
            sb.append(answers);
        }
        sb.append('\n');

        // Seal.
        sb.append(genSeal());

        return sb.toString();
    }

    /**
     * Run sanning on a file and updates result.
     * @param args <path> <identity> <answer>
     */
    public static void main(String[] args) throws Throwable {
        // Usage.
        if ((args.length < 1) || (args.length > 4)) {
            System.out.println("sanning.sh <sanning path> [<identity> [<personal code> [<answer option>]]]");
            System.exit(2);
        }
        File file = new File(args[0]);
        Sanning sanning = new Sanning(file.getName(), file.getParent());

        // Questionnaire.
        System.out.println(sanning.title);
        System.out.println(sanning.text + '\n');

        // Lookup or submit answer.
        Answer answer = null;
        String msg = null;
        if (args.length > 1) {
            try {
                if ((args.length == 2) || (args.length == 3)) {
                    // Lookup previous answer.
                    String ik = args[1];
                    String p = (args.length == 3) ? args[2] : null;
                    String ak = sanning.generateAK(ik);

                    answer = sanning.lookupAnswer(ak, p);

                    // Show answer for reference.
                    msg = (answer != null) ? "Answer found!" : "No answer found for identity '" + ik + "'.";
                } else { // args.length == 4
                    // Submit new answer.
                    String ik = args[1];
                    String p = args[2];
                    String ak = sanning.generateAK(ik);
                    answer = sanning.lookupAnswer(ak, p);

                    if (answer == null) {
                        // Submit new answer.
                        int optionNum = Integer.parseInt(args[3]);
                        answer = sanning.doAnswer(ik, optionNum, p);
                        msg = "Thank you!\nYou answer has been recorded.";
                    } else {
                        msg = "Previous answer found!";
                    }
                }

            } catch (IllegalStateException | IllegalArgumentException e) {
                msg = "ERROR: " + e.getMessage();
            }
        }

        // Summary.
        int total = 0;
        for (int count : sanning.summary) {
            total += count;
        }
        for (int ix = 0; ix < sanning.summary.length; ix++) {
            int count = sanning.summary[ix];
            float percentage = total > 0 ? (float)count * 100 / total : 0f;
            System.out.printf("%-16s %6d  %6.2f%%%n", sanning.options[ix], count, percentage);
        }

        // Message.
        if (msg != null) {
            System.out.println('\n' + msg);
        }

        // Answer.
        if (answer != null) {
            System.out.println("\nAnswer:    " + ((answer.o != null) ? answer.o : answer.po));
            System.out.println("Reference: " + answer.ak);
            System.out.println("Time:      " + answer.ts);
        }

        // Last updated time.
        String lastTS = sanning.lastTS();
        if (!lastTS.isEmpty()) {
            System.out.println("\nLast Updated: " + lastTS);
        }
    }

}
