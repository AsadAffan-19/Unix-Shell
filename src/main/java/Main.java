import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {

    static final Set<String> BUILTINS = Set.of(
            "echo", "exit", "type", "pwd", "cd", "jobs");

    static Map<Integer, BackgroundJob> backgroundJobs = new HashMap<>();

    static class BackgroundJob {
        int jobId;
        Process process;
        String command;

        BackgroundJob(int jobId, Process process, String command) {
            this.jobId = jobId;
            this.process = process;
            this.command = command;
        }
    }

    static class ParsedCommand {
        List<String> parts;
        String outFile;
        String errFile;
        boolean appendOut;
        boolean appendErr;

        ParsedCommand(List<String> parts) {
            this.parts = parts;
        }
    }

    static String findExecutable(String command) {
        String path = System.getenv("PATH");
        if (path == null)
            return null;

        for (String dir : path.split(File.pathSeparator)) {
            Path p = Paths.get(dir, command);
            if (Files.exists(p) && Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return null;
    }

    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int i = 0;
        while (i < command.length()) {
            char c = command.charAt(i);

            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                i++;
                continue;
            }

            if (c == '\'') {
                i++;
                while (i < command.length() && command.charAt(i) != '\'') {
                    current.append(command.charAt(i++));
                }
                i++;
            } else if (c == '"') {
                i++;
                while (i < command.length() && command.charAt(i) != '"') {
                    char ch = command.charAt(i);
                    if (ch == '\\' && i + 1 < command.length()) {
                        i++;
                        char escaped = command.charAt(i);
                        if (escaped == '"' || escaped == '\\') {
                            current.append(escaped);
                        } else {
                            current.append('\\').append(escaped);
                        }
                    } else {
                        current.append(ch);
                    }
                    i++;
                }
                i++;
            } else if (c == '\\') {
                i++;
                if (i < command.length()) {
                    current.append(command.charAt(i));
                    i++;
                }
            } else {
                current.append(c);
                i++;
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    static void checkAndReapJobs() {
        List<Integer> toRemove = new ArrayList<>();

        if (backgroundJobs.isEmpty())
            return;

        List<Integer> ids = new ArrayList<>(backgroundJobs.keySet());
        Collections.sort(ids);

        int mostRecent = ids.get(ids.size() - 1);
        Integer secondMost = ids.size() > 1 ? ids.get(ids.size() - 2) : null;

        for (Integer id : ids) {
            BackgroundJob job = backgroundJobs.get(id);

            if (!job.process.isAlive()) {
                String marker = " ";
                if (id == mostRecent)
                    marker = "+";
                else if (secondMost != null && id == secondMost)
                    marker = "-";

                String status = String.format("%-24s", "Done");
                String cmd = job.command.trim();

                if (cmd.endsWith("&")) {
                    cmd = cmd.substring(0, cmd.length() - 1).trim();
                }

                System.out.println("[" + id + "]" + marker + " " + status + cmd);
                toRemove.add(id);
            }
        }

        for (Integer id : toRemove) {
            backgroundJobs.remove(id);
        }
    }

    static String executeBuiltin(List<String> parts) {
        String cmd = parts.get(0);

        switch (cmd) {
            case "echo":
                return String.join(" ", parts.subList(1, parts.size())) + "\n";

            case "pwd":
                return System.getProperty("user.dir") + "\n";

            case "type":
                if (parts.size() < 2)
                    return "";
                String target = parts.get(1);

                if (BUILTINS.contains(target)) {
                    return target + " is a shell builtin\n";
                }

                String path = findExecutable(target);
                if (path != null) {
                    return target + " is " + path + "\n";
                }

                return target + ": not found\n";

            case "jobs":
                return getJobsOutput();
        }

        return "";
    }

    static String getJobsOutput() {
        if (backgroundJobs.isEmpty())
            return "";

        StringBuilder output = new StringBuilder();
        List<Integer> ids = new ArrayList<>(backgroundJobs.keySet());
        Collections.sort(ids);

        int mostRecent = ids.get(ids.size() - 1);
        Integer secondMost = ids.size() > 1 ? ids.get(ids.size() - 2) : null;

        List<Integer> toRemove = new ArrayList<>();

        for (Integer id : ids) {
            BackgroundJob job = backgroundJobs.get(id);

            String marker = " ";
            if (id == mostRecent)
                marker = "+";
            else if (secondMost != null && id == secondMost)
                marker = "-";

            boolean done = !job.process.isAlive();

            String status = String.format("%-24s", done ? "Done" : "Running");
            String cmd = job.command;

            if (done) {
                cmd = cmd.trim();
                if (cmd.endsWith("&")) {
                    cmd = cmd.substring(0, cmd.length() - 1).trim();
                }
                toRemove.add(id);
            }

            output.append("[").append(id).append("]")
                    .append(marker)
                    .append(" ")
                    .append(status)
                    .append(cmd)
                    .append("\n");
        }

        for (Integer id : toRemove) {
            backgroundJobs.remove(id);
        }

        return output.toString();
    }

    static Process runExternal(List<String> parts, boolean background) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.inheritIO();

        Process process = pb.start();

        if (!background) {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
            }
            return null;
        }

        return process;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            checkAndReapJobs();

            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine())
                break;
            String line = scanner.nextLine();

            List<String> parts = tokenize(line);
            if (parts.isEmpty())
                continue;

            String outputFile = null;

            for (int i = 0; i < parts.size(); i++) {

                if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                    outputFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));

                    break;

                }
            }

            boolean background = false;
            if (parts.get(parts.size() - 1).equals("&")) {
                background = true;
                parts.remove(parts.size() - 1);
            }

            String cmd = parts.get(0);

            if (cmd.equals("exit"))
                break;

            if (cmd.equals("cd")) {
                if (parts.size() < 2)
                    continue;

                String dir = parts.get(1);

                if (dir.equals("~")) {

                    dir = System.getenv("HOME");

                }

                Path targetPath;

                if (Paths.get(dir).isAbsolute()) {
                    targetPath = Paths.get(dir);
                } else {
                    Path currentDir = Paths.get(System.getProperty("user.dir"));
                    targetPath = currentDir.resolve(dir).normalize();
                }

                if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                    System.setProperty("user.dir", targetPath.toRealPath().toString());
                } else {
                    System.out.println("cd: " + dir + ": No such file or directory");
                }

                continue;
            }

            if (BUILTINS.contains(cmd)) {
                String output = executeBuiltin(parts);

                if (!output.isEmpty()) {

                    if (outputFile != null) {
                        Files.writeString(
                                Paths.get(outputFile),
                                output);
                    } else {
                        System.out.print(output);
                    }
                }

                continue;
            }

            String executable = findExecutable(cmd);

            if (executable != null) {
                if (background) {
                    int id = 1;
                    while (backgroundJobs.containsKey(id))
                        id++;

                    Process p = runExternal(parts, true);
                    backgroundJobs.put(id, new BackgroundJob(id, p, line));

                    System.out.println("[" + id + "] " + p.pid());
                } else {
                    runExternal(parts, false);
                }
            } else {
                System.out.println(cmd + ": command not found");
            }
        }
    }
}