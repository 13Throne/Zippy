package org.vosk;
public class Recognizer implements AutoCloseable {
    public Recognizer(Model m, float sampleRate) {}
    public boolean acceptWaveForm(short[] data, int len) { return false; }
    public String getResult() { return null; }
    public String getPartialResult() { return null; }
    public String getFinalResult() { return null; }
    public void reset() {}
    public void setWords(boolean words) {}
    public void close() {}
}
