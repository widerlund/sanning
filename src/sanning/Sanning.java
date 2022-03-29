package sanning;

import static sanning.Util.hash;
import static sanning.Util.toBase64;
import static sanning.Util.toBytes;
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

    static final int AK_LEN = 4;  // Default anonymous key length (characters).

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
     * @param ik     identity key
     * @param option answer option
     * @return timestamped answer
     */
    Answer doAnswer(String ik, int option) throws IOException {
        // Generate AK.
        String ak = generateAK(ik);

        // Verify that AK has not answered.
        Answer oldAnswer = lookupAnswer(ak);
        if (oldAnswer != null) {
            return oldAnswer;
        }

        // Update summary.
        if (option >= summary.length) {
            throw new IllegalArgumentException("invalid answer: " + option);
        }
        summary[option]++;

        // Timestamp.
        String ts = toISO8601(System.currentTimeMillis());

        // Create new answer line.
        answers.append(ts).append(" ").append(ak).append(":").append(option).append('\n');

        // Persist (always for now!).
        persist();

        return new Answer(options[option], option, ak, ts, false);
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
    Answer lookupAnswer(String ak) {
        int ix1 = (answers != null) ? answers.indexOf(" " + ak + ":") : -1;
        if (ix1 != -1) {
            int ix2 = answers.indexOf("\n", ix1);
            String answerLine = answers.substring(ix1 - Util.ISO_8601_LEN, ix2);

            // <ts> <ak>:<option number>
            ix1 = answerLine.indexOf(' ');
            String ts = answerLine.substring(0, ix1);
            ix2 = answerLine.indexOf(':', ix1);
            int optionNum = Integer.parseInt(answerLine.substring(ix2 + 1));
            String option = options[optionNum];
            return new Answer(option, optionNum, ak, ts, true);
        } else {
            return null;
        }
    }


    /** Timestamp of last answer. */
    String lastTS() {
        String lastAnswerLine = answers.substring(Math.max(0, answers.length() - (Util.ISO_8601_LEN + 1 + Sanning.AK_LEN + 1 + 4)));
                                                                  // NOTE: 4 is maximum length of available number of options -^
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
        byte[] akBytes = hash(toBytes(text), toBytes(ik));
        String akBase64 = toBase64(akBytes);
        return akBase64.substring(0, AK_LEN);
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
                String key = line.substring(Util.ISO_8601_LEN + 1, Util.ISO_8601_LEN + 1 + Sanning.AK_LEN);
                if (!keySet.add(key)) {
                    throw new IllegalStateException("duplicate key: " + key);
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

        return toHex(hash(toBytes(title), toBytes(text), toBytes(options), toBytes(answers)));
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
        sb.append(genSeal()).append('\n');

        return sb.toString();
    }

    /**
     * Run sanning on a file and updates result.
     * @param args <path> <identity> <answer>
     */
    public static void main(String[] args) throws Throwable {
        // Usage.
        if ((args.length < 1) || (args.length > 3)) {
            System.out.println("sanning.sh <sanning path> [<identity> [<answer option>]]");
            System.exit(2);
        }
        File file = new File(args[0]);
        Sanning sanning = new Sanning(file.getName(), file.getParent());

        System.out.println(sanning.title);
        System.out.println(sanning.text + '\n');

        for (String option : sanning.options) {
            System.out.println(option);
        }

        String ak = null;
        if (args.length > 1) {
            String ik = args[1];
            ak = sanning.generateAK(ik);
        }

        if (args.length == 2) {
            // Lookup previous answer.
            Answer answer = sanning.lookupAnswer(ak);
            System.out.println("\nANSWER:" + (answer != null ? "\n" + answer.ak : " not found"));
        } else if (args.length == 3) {
            // Add new answer.
            int option = Integer.parseInt(args[2]);
            try {
                Answer answer = sanning.doAnswer(ak, option);
                sanning.persist();
                System.out.println("\nYour answer:" + answer.option);
                System.out.println("Reference: " + ak);
                System.out.println("Time: " + answer.ts);
            } catch (IllegalStateException | IllegalArgumentException e) {
                System.out.println("ERROR:\n" + e.getMessage());
            }
        }
    }

}
