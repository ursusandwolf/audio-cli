package com.stt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhisperRunnerTest {

    @Test
    void normalizeTranscriptionCollapsesBlankLines() {
        String text = WhisperRunner.normalizeTranscription("  Первая строка\n\nВторая строка  \n");

        assertEquals("Первая строка Вторая строка", text);
    }

    @Test
    void normalizeTranscriptionReturnsEmptyStringForBlankInput() {
        assertEquals("", WhisperRunner.normalizeTranscription(" \n\t "));
    }

    @Test
    void extractProgressPercentParsesWhisperProgressLine() {
        assertEquals(35, WhisperRunner.extractProgressPercent("whisper_print_progress_callback: progress =  35%"));
    }

    @Test
    void extractProgressPercentReturnsNullForNonProgressLine() {
        assertNull(WhisperRunner.extractProgressPercent("main: processing '/tmp/audio.wav'"));
    }

    @Test
    void formatProgressBarRendersFixedWidthBar() {
        assertEquals("[##########----------]  50%", WhisperRunner.formatProgressBar(50));
    }

    @Test
    void resolveDefaultThreadLimitIsConservative() {
        int threadLimit = WhisperRunner.resolveDefaultThreadLimit();

        assertEquals(1, threadLimit);
    }

    @Test
    void terminateProcessReturnsWhenProcessStopsGracefully() throws Exception {
        FakeProcess process = new FakeProcess(true, false, false);

        WhisperRunner.terminateProcess(process, 42L);

        assertEquals(true, process.destroyCalled);
        assertEquals(false, process.destroyForciblyCalled);
    }

    @Test
    void terminateProcessForcesKillWhenNeeded() throws Exception {
        FakeProcess process = new FakeProcess(false, true, false);

        WhisperRunner.terminateProcess(process, 42L);

        assertEquals(true, process.destroyCalled);
        assertEquals(true, process.destroyForciblyCalled);
    }

    @Test
    void terminateProcessFailsWhenProcessStaysAlive() {
        FakeProcess process = new FakeProcess(false, false, true);

        IOException error = assertThrows(
            IOException.class,
            () -> WhisperRunner.terminateProcess(process, 42L)
        );

        assertEquals("Failed to terminate whisper.cpp process PID 42", error.getMessage());
    }

    @Test
    void createShutdownHookKillsAliveProcess() throws Exception {
        FakeProcess process = new FakeProcess(true, false, false);

        Thread hook = WhisperRunner.createShutdownHook(process, 77L);
        hook.run();

        assertEquals(true, process.destroyCalled);
    }

    static final class FakeProcess extends Process {
        private final boolean stopsAfterDestroy;
        private final boolean stopsAfterForce;
        private final boolean staysAlive;
        boolean destroyCalled;
        boolean destroyForciblyCalled;
        private boolean alive = true;

        FakeProcess(boolean stopsAfterDestroy, boolean stopsAfterForce, boolean staysAlive) {
            this.stopsAfterDestroy = stopsAfterDestroy;
            this.stopsAfterForce = stopsAfterForce;
            this.staysAlive = staysAlive;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (staysAlive) {
                return false;
            }
            return !alive;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            if (stopsAfterDestroy) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            if (stopsAfterForce) {
                alive = false;
            }
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
