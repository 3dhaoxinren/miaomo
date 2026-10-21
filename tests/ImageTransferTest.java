package dev.inknfclab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

public final class ImageTransferTest {
    private static final class Clock implements ImageTransfer.RefreshClock {
        long time;
        @Override public long now() { return time; }
        @Override public void sleep(long milliseconds) { time += milliseconds; }
    }

    private static final class Step {
        final long duration;
        final byte[] response;
        final Exception failure;
        Step(long duration, byte[] response, Exception failure) {
            this.duration = duration;
            this.response = response;
            this.failure = failure;
        }
    }

    private static final class Link implements ImageTransfer.RefreshLink {
        final Clock clock;
        final List<Step> steps;
        final List<Integer> timeouts = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        int timeout = 3000;
        Link(Clock clock, Step... steps) { this.clock = clock; this.steps = Arrays.asList(steps); }
        @Override public int getTimeout() { return timeout; }
        @Override public void setTimeout(int milliseconds) { timeout = milliseconds; timeouts.add(milliseconds); }
        @Override public byte[] transceive(byte[] command) throws Exception {
            Step step = steps.get(commands.size());
            commands.add(Protocol.hex(command));
            if (step.duration > timeout) {
                clock.time += timeout;
                throw new TimeoutException("transport timeout");
            }
            clock.time += step.duration;
            if (step.failure != null) throw step.failure;
            return step.response;
        }
    }

    private static final class Progress implements ImageTransfer.Progress {
        final List<String> logs = new ArrayList<>();
        boolean complete;
        @Override public void update(String message, int completed, int total) { complete = message.equals("屏幕刷新完成"); }
        @Override public boolean cancelled() { return false; }
        @Override public void trace(String message) { logs.add(message); }
    }

    public static void main(String[] args) throws Exception {
        Clock clock = new Clock();
        Link link = new Link(clock, reply(8000, "9000"), reply(20, "019000"), reply(20, "009000"));
        Progress progress = new Progress();
        ImageTransfer.refresh(link, clock, progress, 476);
        require(progress.complete);
        require(link.commands.equals(Arrays.asList("F0D4948000", "F0DE000001", "F0DE000001")));
        require(link.timeouts.get(0) == 60000);
        require(link.timeouts.get(1) == 52000);
        require(link.timeout == 3000);
        require(progress.logs.stream().anyMatch(message -> message.startsWith("DE RX 009000")));

        clock = new Clock();
        link = new Link(clock, reply(59000, "9000"), reply(100, "019000"), reply(500, "009000"));
        progress = new Progress();
        try {
            ImageTransfer.refresh(link, clock, progress, 476);
            throw new AssertionError("D4 time must consume the shared refresh budget");
        } catch (TimeoutException expected) {
            require(!progress.complete);
            require(clock.time == 60000);
            require(link.timeout == 3000);
            require(link.timeouts.contains(400));
        }

        clock = new Clock();
        link = new Link(clock, new Step(300, null, new IOException("Tag lost")));
        progress = new Progress();
        try {
            ImageTransfer.refresh(link, clock, progress, 476);
            throw new AssertionError("D4 failure must stop the session");
        } catch (IOException expected) {
            require(link.commands.size() == 1);
            require(link.timeout == 3000);
            require(progress.logs.stream().anyMatch(message -> message.contains("REFRESH_FAILED · stage=D4")));
        }

        // Replay the real screen failure: upload accepted, D4 accepted, busy, undervoltage.
        clock=new Clock();link=new Link(clock,reply(4142,"9000"),reply(7,"019000"),reply(20,"68CA"));progress=new Progress();
        try {
            ImageTransfer.refresh(link,clock,progress,476);
            throw new AssertionError("Undervoltage must stop without reporting completion or resending D4");
        } catch(IllegalStateException expected) {
            require(expected.getMessage().contains("68CA"));require(!progress.complete);
            require(link.commands.equals(Arrays.asList("F0D4948000","F0DE000001","F0DE000001")));
            require(link.timeout==3000);
        }

        clock=new Clock();link=new Link(clock,reply(6000,"9000"),reply(10,"019000"),reply(10,"009000"));progress=new Progress();
        ImageTransfer.refresh(link,clock,progress,476,5000);
        require(link.commands.get(0).equals("F0D4B28000"));require(progress.complete);
        require(link.timeout==3000);
        require(Protocol.hex(ImageTransfer.refreshCommand(2000)).equals("F0D4948000"));
        for(int invalid:new int[]{0,99,101,12800}) {
            clock=new Clock();link=new Link(clock);progress=new Progress();
            try {ImageTransfer.refresh(link,clock,progress,476,invalid);throw new AssertionError("Invalid power wait accepted");}
            catch(IllegalArgumentException expected){require(link.commands.isEmpty());}
        }
        System.out.println("Image refresh timing tests passed");
    }

    private static Step reply(long duration, String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++)
            bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        return new Step(duration, bytes, null);
    }

    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
}
