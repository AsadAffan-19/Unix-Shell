
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                current.append('\\');

            } else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                escaped = true;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    static class Job {

        int jobNumber;
        long pid;
        String command;
        Process process;

        Job(int jobNumber, long pid, String command, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    static void reapJobs(List<Job> jobs) {

        List<Job> completedJobs = new ArrayList<>();

        for (int i = 0; i < jobs.size(); i++) {

            Job job = jobs.get(i);

            if (!job.process.isAlive()) {

                char marker = ' ';

                if (i == jobs.size() - 1) {
                    marker = '+';
                } else if (i == jobs.size() - 2) {
                    marker = '-';
                }

                String cmd = job.command;

                if (cmd.endsWith(" &")) {
                    cmd = cmd.substring(0, cmd.length() - 2);
                }

                System.out.printf(
                        "[%d]%c  %-24s%s%n",
                        job.jobNumber,
                        marker,
                        "Done",
                        cmd
                );

                completedJobs.add(job);
            }
        }

        jobs.removeAll(completedJobs);
    }

    static int getNextJobNumber(List<Job> jobs) {

        if (jobs.isEmpty()) {
            return 1;
        }

        int maxJobNumber = 0;

        for (Job job : jobs) {
            maxJobNumber = Math.max(maxJobNumber, job.jobNumber);
        }

        return maxJobNumber + 1;
    }

    static String runBuiltin(List<String> command) {

        if (command.get(0).equals("echo")) {

            StringBuilder sb = new StringBuilder();

            for (int i = 1; i < command.size(); i++) {
                if (i > 1) {
                    sb.append(" ");
                }
                sb.append(command.get(i));
            }

            sb.append("\n");

            return sb.toString();
        }

        if (command.get(0).equals("type")) {

            String arg = command.get(1);

            if (arg.matches("type|echo|exit|pwd|cd|jobs")) {
                return arg + " is a shell builtin\n";
            }

            return arg + ": not found\n";
        }

        return "";
    }

    static boolean isBuiltin(String cmd) {
        return cmd.matches("echo|type|pwd|cd|exit|jobs");
    }

    static List<List<String>> parsePipeline(List<String> tokens) {

        List<List<String>> commands = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals("|")) {
                commands.add(current);
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }

        commands.add(current);

        return commands;
    }

    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage

        Scanner sc = new Scanner(System.in);
        List<Job> jobs = new ArrayList<>();

        while (true) {

            reapJobs(jobs);

            System.out.print("$ ");
            String input = sc.nextLine();

            List<String> tokens = parseCommand(input);

            boolean background = false;

            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens.remove(tokens.size() - 1);
            }

            String stdoutRedirect = null;
            String stderrRedirect = null;

            boolean appendStdout = false;
            boolean appendStderr = false;

            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                    stdoutRedirect = tokens.get(i + 1);
                    appendStdout = false;
                    tokens = new ArrayList<>(tokens.subList(0, i));
                    break;
                }

                if (tokens.get(i).equals(">>") || tokens.get(i).equals("1>>")) {
                    stdoutRedirect = tokens.get(i + 1);
                    appendStdout = true;
                    tokens = new ArrayList<>(tokens.subList(0, i));
                    break;
                }

                if (tokens.get(i).equals("2>")) {
                    stderrRedirect = tokens.get(i + 1);
                    appendStderr = false;
                    tokens = new ArrayList<>(tokens.subList(0, i));
                    break;
                }

                if (tokens.get(i).equals("2>>")) {
                    stderrRedirect = tokens.get(i + 1);
                    appendStderr = true;
                    tokens = new ArrayList<>(tokens.subList(0, i));
                    break;
                }
            }

            boolean hasPipe = tokens.contains("|");

            if (hasPipe) {

                List<List<String>> pipelineCommands
                        = parsePipeline(tokens);

                List<String> leftCommand = pipelineCommands.get(0);
                List<String> rightCommand = pipelineCommands.get(1);

                if (pipelineCommands.size() == 2 && isBuiltin(leftCommand.get(0))) {

                    String output = runBuiltin(leftCommand);

                    ProcessBuilder pb = new ProcessBuilder(rightCommand);
                    pb.directory(new File(System.getProperty("user.dir")));

                    Process p = pb.start();

                    p.getOutputStream().write(output.getBytes());
                    p.getOutputStream().close();

                    p.getInputStream().transferTo(System.out);

                    p.waitFor();
                    continue;
                }

                if (pipelineCommands.size() == 2 && isBuiltin(rightCommand.get(0))) {

                    ProcessBuilder pb = new ProcessBuilder(leftCommand);
                    pb.directory(new File(System.getProperty("user.dir")));

                    Process p = pb.start();

                    p.waitFor();

                    System.out.print(runBuiltin(rightCommand));

                    continue;
                }

                List<ProcessBuilder> builders = new ArrayList<>();

                for (List<String> cmd : pipelineCommands) {

                    ProcessBuilder pb = new ProcessBuilder(cmd);

                    pb.directory(
                            new File(System.getProperty("user.dir"))
                    );

                    pb.redirectError(
                            ProcessBuilder.Redirect.INHERIT
                    );

                    builders.add(pb);
                }

                List<Process> processes
                        = ProcessBuilder.startPipeline(builders);

                Process lastProcess
                        = processes.get(processes.size() - 1);

                lastProcess.getInputStream().transferTo(System.out);

                for (Process p : processes) {
                    p.waitFor();
                }

                continue;
            }

            if (input.equals("exit")) {
                break;
            } else if (!tokens.isEmpty() && tokens.get(0).equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < tokens.size(); i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(tokens.get(i));
                }
                output.append("\n");

                if (stderrRedirect != null) {
                    new java.io.FileWriter(stderrRedirect, appendStderr).close();
                }

                if (stdoutRedirect != null) {
                    try (java.io.FileWriter writer
                            = new java.io.FileWriter(stdoutRedirect, appendStdout)) {

                        writer.write(output.toString());
                    }
                } else {
                    System.out.print(output.toString());
                }
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (!tokens.isEmpty() && tokens.get(0).equals("jobs")) {

                List<Job> completedJobs = new ArrayList<>();

                for (int i = 0; i < jobs.size(); i++) {

                    Job job = jobs.get(i);
                    char marker = ' ';

                    if (i == jobs.size() - 1) {
                        marker = '+';
                    } else if (i == jobs.size() - 2) {
                        marker = '-';
                    }

                    if (job.process.isAlive()) {

                        System.out.printf(
                                "[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Running",
                                job.command
                        );

                    } else {

                        String cmd = job.command;

                        if (cmd.endsWith(" &")) {
                            cmd = cmd.substring(0, cmd.length() - 2);
                        }

                        System.out.printf(
                                "[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Done",
                                cmd
                        );
                        completedJobs.add(job);
                    }
                }

                jobs.removeAll(completedJobs);
            } else if (input.startsWith("cd ")) {
                String directory = input.substring(3);

                File targetDir;
                if (directory.equals("~")) {

                    String home = System.getenv("HOME"); // for Linux
                    if (home == null) { // for Windows
                        home = System.getProperty("user.home");
                    }

                    targetDir = new File(home);
                } else {
                    File currentDir = new File(System.getProperty("user.dir"));

                    if (directory.startsWith("/")) {
                        targetDir = new File(directory);
                    } else {
                        targetDir = new File(currentDir, directory);
                    }
                }

                if (targetDir.exists() && targetDir.isDirectory()) {
                    System.setProperty("user.dir", targetDir.getCanonicalPath());
                } else {
                    if (stderrRedirect != null) {
                        try (java.io.PrintWriter writer
                                = new java.io.PrintWriter(new File(stderrRedirect))) {

                            writer.println("cd: " + directory + ": No such file or directory");
                        }
                    } else {
                        System.err.println("cd: " + directory + ": No such file or directory");
                    }
                }
            } else if (input.startsWith("type ")) {
                String command = tokens.get(1);

                if (command.matches("type|echo|exit|pwd|cd|jobs")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    String path = System.getenv("PATH");
                    boolean found = false;

                    for (String dir : path.split(File.pathSeparator)) {

                        File file = new File(dir, command);

                        if (file.isFile() && file.canExecute()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        if (stderrRedirect != null) {
                            try (java.io.PrintWriter writer
                                    = new java.io.PrintWriter(new File(stderrRedirect))) {

                                writer.println(command + ": not found");
                            }
                        } else {
                            System.err.println(command + ": not found");
                        }
                    }

                }
            } else {

                List<String> command = tokens;
                String path = System.getenv("PATH");
                boolean found = false;

                for (String dir : path.split(File.pathSeparator)) {

                    File file = new File(dir, command.get(0));

                    if (file.isFile() && file.canExecute()) {
                        ProcessBuilder pb = new ProcessBuilder(command);

                        pb.directory(new File(System.getProperty("user.dir")));

                        if (stdoutRedirect != null) {

                            if (appendStdout) {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.appendTo(
                                                new File(stdoutRedirect)
                                        )
                                );
                            } else {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.to(
                                                new File(stdoutRedirect)
                                        )
                                );
                            }

                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }

                        if (stderrRedirect != null) {
                            if (appendStderr) {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.appendTo(
                                                new File(stderrRedirect)
                                        )
                                );
                            } else {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.to(
                                                new File(stderrRedirect)
                                        )
                                );
                            }
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                        Process process = pb.start();

                        if (background) {

                            int jobNumber = getNextJobNumber(jobs);

                            jobs.add(
                                    new Job(
                                            jobNumber,
                                            process.pid(),
                                            input,
                                            process
                                    )
                            );

                            System.out.println("[" + jobNumber + "] " + process.pid());

                        } else {
                            process.waitFor();
                        }

                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (stderrRedirect != null) {
                        try (java.io.FileWriter writer
                                = new java.io.FileWriter(stderrRedirect, appendStderr)) {

                            writer.write(command + ": not found\n");
                        }
                    } else {
                        System.err.println(command.get(0) + ": command not found");
                    }
                }
            }
        }
    }
}