package com.ryanmichela.sshd;

import cn.nukkit.Server;
import cn.nukkit.utils.LogLevel;
import com.ryanmichela.sshd.implementations.SSHDCommandSender;
import jline.console.ConsoleReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.StreamHandler;

public class ConsoleShellFactory implements Factory<Command> {

    static SSHDCommandSender sshdCommandSender = new SSHDCommandSender();

    public Command get() {
        return this.create();
    }

    public Command create() {
        return new ConsoleShell();
    }

    public static class ConsoleShell implements Command, Runnable {

        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Environment environment;
        private Thread thread;

        StreamHandlerAppender streamHandlerAppender;
        public static ConsoleReader consoleReader;

        public InputStream getIn() {
            return in;
        }

        public OutputStream getOut() {
            return out;
        }

        public OutputStream getErr() {
            return err;
        }

        public Environment getEnvironment() {
            return environment;
        }

        public void setInputStream(InputStream in) {
            this.in = in;
        }

        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        public void start(Environment env) throws IOException {
            try {
                consoleReader = new ConsoleReader(in, new FlushyOutputStream(out), new SshTerminal());
                consoleReader.setExpandEvents(true);
//                consoleReader.addCompleter(new ConsoleCommandCompleter());

                StreamHandler streamHandler = new FlushyStreamHandler(out, new ConsoleLogFormatter(), consoleReader);
                streamHandlerAppender = new StreamHandlerAppender(streamHandler);

                ((Logger) LogManager.getRootLogger()).addAppender(streamHandlerAppender);

                environment = env;
                thread = new Thread(this, "SSHD ConsoleShell " + env.getEnv().get(Environment.ENV_USER));
                thread.start();
            } catch (Exception e) {
                throw new IOException("Error starting shell", e);
            }
        }

        public void destroy() {
            ((Logger) LogManager.getRootLogger()).removeAppender(streamHandlerAppender);
        }

        public synchronized void run() {
            try {

                sshdCommandSender.addAttachment(SshdPlugin.instance, Server.BROADCAST_CHANNEL_USERS);

                if (!SshdPlugin.instance.getConfig().getString("mode").equals("RPC"))
                    printPreamble(consoleReader);
                while (true) {
                    String command = consoleReader.readLine("\r", null);

                    if (command == null) continue;

                    if (command.equals("exit") || command.equals("quit")) break;
                    Server.getInstance().getScheduler().scheduleTask(SshdPlugin.instance, () -> {
                        if (SshdPlugin.instance.getConfig().getString("mode").equals("RPC") &&
                                command.startsWith("rpc")) {
                            //NO ECHO NO PREAMBLE AND SHIT
                            String cmd = command.substring("rpc".length() + 1);
                            Server.getInstance().dispatchCommand(sshdCommandSender, cmd);
                        } else {
                            SshdPlugin.instance.getLogger()
                                    .info("<" + environment.getEnv().get(Environment.ENV_USER) + "> " + command);
                            Server.getInstance().dispatchCommand(sshdCommandSender, command);
                        }

                    });
                }
            } catch (IOException e) {
                SshdPlugin.instance.getLogger().log(LogLevel.EMERGENCY, "Error processing command from SSH", e);
            } finally {
                callback.onExit(0);
            }
        }

        private void printPreamble(ConsoleReader consoleReader) throws IOException {
            consoleReader.println("  __  __       _ _                    _____            __ _   " + "\r"); 
            consoleReader.println("|  \/  |     | | |                  / ____|          / _| |  " + "\r");
            consoleReader.println("| \  / | __ _| | |_ _ __ ___  _ __ | |     _ __ __ _| |_| |_ " + "\r");
            consoleReader.println("| |\/| |/ _` | | __| '__/ _ \| '_ \| |    | '__/ _` |  _| __|" + "\r");
            consoleReader.println("| |  | | (_| | | |_| | | (_) | | | | |____| | | (_| | | | |_ " + "\r");
            consoleReader.println("|_|  |_|\__,_|_|\__|_|  \___/|_| |_|\_____|_|  \__,_|_|  \__|" + "\r");
            consoleReader.println("Connected to: MaltronCraft" + "\r");
            consoleReader.println("\r");
            consoleReader.println("Type 'exit' to exit the shell." + "\r");
            consoleReader.println("===============================================" + "\r");
        }
    }
}
