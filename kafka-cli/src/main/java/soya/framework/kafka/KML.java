package soya.framework.kafka;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Date;

public class KML extends KafkaCommands {

    private static File home;
    private static File cmmDir;
    private static File bodDir;
    private static File logDir;

    static {
        options.addOption(Option.builder("b")
                .longOpt("businessObject")
                .hasArg(true)
                .desc("Business object.")
                .required(false)
                .build());

        options.addOption(Option.builder("l")
                .longOpt("log")
                .hasArg(false)
                .desc("Enable log")
                .required(false)
                .build());

        options.addOption(Option.builder("u")
                .longOpt("useCase")
                .hasArg(true)
                .desc("Test case")
                .required(false)
                .build());

        File bin = new File(Paths.get("").toAbsolutePath().toString());
        home = bin.getParentFile();

        cmmDir = new File(home, "CMM");

        bodDir = new File(home, "BusinessObjects");
        if (!bodDir.exists()) {
            bodDir.mkdir();
        }

        logDir = new File(home, "logs");
        if (!logDir.exists()) {
            logDir.mkdir();
        }

    }

    public static void main(String[] args) {
        try {
            CommandLine commandLine = build(args);
            if (commandLine.hasOption("b") && commandLine.getOptionValue("b") != null) {
                File bod = new File(bodDir, commandLine.getOptionValue("b"));
                if (!bod.exists()) {
                    throw new IllegalArgumentException("Business object does not exist: " + commandLine.getOptionValue("b"));
                }

                File cmdFile = new File(bod, "test/cmd.json");
                if (!cmdFile.exists()) {
                    throw new IllegalArgumentException("File does not exist: " + cmdFile.getAbsolutePath());
                }

                String testCase = "default";
                if (commandLine.hasOption("u")) {
                    testCase = commandLine.getOptionValue("u");

                }

                JsonObject obj = JsonParser.parseReader(new FileReader(cmdFile)).getAsJsonObject();
                if (obj.get(testCase) == null) {
                    throw new IllegalArgumentException("Test case does not exist: " + testCase);
                }

                String cmd = obj.get(testCase).getAsString();
                CommandLine recompiled = recompile(cmd, bod, commandLine);
                String result = execute(recompiled);
                System.out.println(result);

                if (commandLine.hasOption("l")) {
                    File log = new File(logDir, commandLine.getOptionValue("b") + ".log");
                    if (!log.exists()) {
                        log.createNewFile();
                    }

                    FileUtils.write(log, result, Charset.defaultCharset());
                }

            } else {
                System.out.println(execute(commandLine));
                System.out.println();
                System.out.println();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static CommandLine recompile(String cmd, File dir, CommandLine override) throws ParseException {
        JsonObject jsonObject = new JsonObject();
        CommandLine commandLine = build(cmd);
        Option[] opts = commandLine.getOptions();
        for (Option opt : opts) {
            if ("s".equals(opt.getOpt())) {
                String path = opt.getValue();

                File file = new File(path);
                if (file.exists()) {
                    // do nothing:

                } else {
                    if (!path.startsWith("avsc/")) {
                        path = "avsc/" + path;
                    }

                    path = new File(cmmDir, path).getAbsolutePath();
                }
                path = path.replaceAll("\\\\", "/");
                jsonObject.addProperty("s", path);

            } else if ("m".equals(opt.getOpt())) {
                String msgFile = opt.getValue();
                if(new File(msgFile).exists()) {
                    // do nothing:
                } else {
                    if (!msgFile.startsWith("test/")) {
                        msgFile = "test/" + msgFile;
                    }
                    msgFile = new File(dir, msgFile).getAbsolutePath();
                }

                msgFile = msgFile.replaceAll("\\\\", "/");
                jsonObject.addProperty("m", msgFile);

            } else {
                jsonObject.addProperty(opt.getOpt(), commandLine.getOptionValue(opt.getOpt()));

            }
        }

        if (override.hasOption("e")) {
            jsonObject.addProperty("e", override.getOptionValue("e"));
        }

        if (override.hasOption("c")) {
            jsonObject.addProperty("c", override.getOptionValue("c"));
        }

        if (override.hasOption("p")) {
            jsonObject.addProperty("p", override.getOptionValue("p"));
        }

        StringBuilder builder = new StringBuilder();

        builder.append("################################## ")
                .append(DATE_FORMAT.format(new Date()))
                .append(" ##################################")
                .append("\n").append("\n");
        jsonObject.entrySet().forEach(e -> {
            String v = "";
            if(e.getValue() != null) {
                v = e.getValue().getAsString();
            }
            builder.append("\t").append("-").append(e.getKey()).append(" ").append(v).append("\n");
        });

        builder.append("\n")
                .append("##############################################################################################")
                .append("\n\n");

        System.out.println(builder.toString());

        return build(jsonObject);
    }
}
