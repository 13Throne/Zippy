package com.zippy.ear;

import androidx.core.app.FrameMetricsAggregator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;

/* JADX INFO: compiled from: Voice.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0014\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u000b\n\u0002\u0010\u0002\n\u0002\b\u0003\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nJ\u0016\u0010\u000b\u001a\u00020\n2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u0005J2\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\n2\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\n0\u00112\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\n0\u00112\u0006\u0010\u0013\u001a\u00020\u0005J\u0018\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\n2\u0006\u0010\u0017\u001a\u00020\nH\u0002J \u0010\u0018\u001a\u00020\n2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\u00052\u0006\u0010\u0019\u001a\u00020\u0015H\u0002J\u0010\u0010\u001a\u001a\u00020\u00152\u0006\u0010\u001b\u001a\u00020\u0015H\u0002J\u0010\u0010\u001c\u001a\u00020\u00152\u0006\u0010\u001d\u001a\u00020\u0015H\u0002J\u0014\u0010\u001e\u001a\u00020\u0015*\u00020\u00152\u0006\u0010\u001f\u001a\u00020\u0015H\u0002J\u0018\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020\n2\u0006\u0010#\u001a\u00020\nH\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082T¢\u0006\u0002\n\u0000¨\u0006$"}, d2 = {"Lcom/zippy/ear/Voice;", "", "<init>", "()V", "BANDS", "", "FFT", "rms", "", "samples", "", "fingerprint", "sampleRate", "matches", "", "live", "wakes", "", "rejects", "sensitivity", "cosine", "", "a", "b", "loudest", "seconds", "hzToMel", "hz", "melToHz", "mel", "pow", "exp", "fft", "", "re", "im", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Voice {
    private static final int BANDS = 24;
    private static final int FFT = 512;
    public static final Voice INSTANCE = new Voice();

    private Voice() {
    }

    public final float rms(float[] samples) {
        Intrinsics.checkNotNullParameter(samples, "samples");
        if (samples.length == 0) {
            return 0.0f;
        }
        double d = 0.0d;
        for (float f : samples) {
            d += (double) (f * f);
        }
        return (float) Math.sqrt(d / ((double) samples.length));
    }

    /* JADX WARN: Code duplicated, block: B:41:0x011c  */
    /* JADX WARN: Code duplicated, block: B:42:0x0132  */
    public final float[] fingerprint(float[] samples, int sampleRate) {
        double d;
        float[] fArr;
        float[] fArr2;
        double d2;
        double[] dArr;
        double d3;
        double d4;
        double d5;
        Voice voice = this;
        Intrinsics.checkNotNullParameter(samples, "samples");
        float[] fArrLoudest = voice.loudest(samples, sampleRate, 0.9d);
        float[] fArr3 = new float[24];
        double dHzToMel = voice.hzToMel(80.0d);
        double d6 = sampleRate;
        double dHzToMel2 = voice.hzToMel(Math.min(4000.0d, (d6 / 2.0d) - 100.0d));
        double[] dArr2 = new double[26];
        int i = 0;
        for (int i2 = 26; i < i2; i2 = 26) {
            dArr2[i] = INSTANCE.melToHz(dHzToMel + (((dHzToMel2 - dHzToMel) * ((double) i)) / 25.0d));
            i++;
        }
        int i3 = 512;
        float[] fArr4 = new float[512];
        float[] fArr5 = new float[512];
        int i4 = 0;
        int i5 = 0;
        while (true) {
            d = 0.0d;
            if (i4 + 512 > fArrLoudest.length || i5 >= 40) {
                break;
            }
            double d7 = 0.0d;
            for (int i6 = 0; i6 < i3; i6++) {
                float f = fArrLoudest[i4 + i6];
                d7 += (double) (f * f);
            }
            double d8 = i3;
            if (d7 / d8 >= 4.0E-4d) {
                int i7 = 0;
                while (i7 < i3) {
                    float[] fArr6 = fArr4;
                    fArr6[i7] = (float) (((double) fArrLoudest[i4 + i7]) * (1.0d - Math.cos((((double) i7) * 6.283185307179586d) / ((double) FrameMetricsAggregator.EVERY_DURATION))) * 0.5d);
                    fArr5[i7] = 0.0f;
                    i7++;
                    fArr4 = fArr6;
                    i3 = 512;
                }
                fArr2 = fArr4;
                voice.fft(fArr2, fArr5);
                int i8 = 0;
                while (i8 < 24) {
                    double d9 = dArr2[i8];
                    double d10 = dArr2[i8 + 2];
                    int i9 = i8 + 1;
                    double d11 = dArr2[i9];
                    float[] fArr7 = fArrLoudest;
                    int iMax = Math.max(1, (int) Math.floor((d9 * d8) / d6));
                    int iMin = Math.min(255, (int) Math.ceil((d10 * d8) / d6));
                    double d12 = 0.0d;
                    int i10 = iMax;
                    double dHypot = 0.0d;
                    while (i10 <= iMin) {
                        int i11 = i9;
                        double[] dArr3 = dArr2;
                        double d13 = (((double) i10) * d6) / d8;
                        if (d13 < d9 || d13 > d11 || d11 <= d9) {
                            if (d13 <= d11 || d13 > d10 || d10 <= d11) {
                                d3 = 0.0d;
                            } else {
                                d4 = d10 - d13;
                                d5 = d10 - d11;
                            }
                            if (d3 > 0.0d) {
                                dHypot += Math.hypot(fArr2[i10], fArr5[i10]) * d3;
                                d12 += d3;
                            }
                            i10++;
                            dArr2 = dArr3;
                            i9 = i11;
                            iMin = iMin;
                            d6 = d6;
                            d8 = d8;
                        } else {
                            d4 = d13 - d9;
                            d5 = d11 - d9;
                        }
                        d3 = d4 / d5;
                        if (d3 > 0.0d) {
                            dHypot += Math.hypot(fArr2[i10], fArr5[i10]) * d3;
                            d12 += d3;
                        }
                        i10++;
                        dArr2 = dArr3;
                        i9 = i11;
                        iMin = iMin;
                        d6 = d6;
                        d8 = d8;
                    }
                    double d14 = d6;
                    int i12 = i9;
                    double[] dArr4 = dArr2;
                    double d15 = d8;
                    fArr3[i8] = fArr3[i8] + ((float) (d12 > 0.0d ? dHypot / d12 : 0.0d));
                    fArrLoudest = fArr7;
                    dArr2 = dArr4;
                    i8 = i12;
                    d6 = d14;
                    d8 = d15;
                }
                fArr = fArrLoudest;
                d2 = d6;
                dArr = dArr2;
                i5++;
            } else {
                fArr = fArrLoudest;
                fArr2 = fArr4;
                d2 = d6;
                dArr = dArr2;
            }
            i4 += 256;
            voice = this;
            fArr4 = fArr2;
            fArrLoudest = fArr;
            dArr2 = dArr;
            d6 = d2;
            i3 = 512;
        }
        float[] fArr8 = new float[24];
        if (i5 == 0) {
            return fArr8;
        }
        int i13 = 0;
        for (int i14 = 24; i13 < i14; i14 = 24) {
            double dLog = Math.log(((double) (fArr3[i13] / i5)) + 1.0d);
            fArr8[i13] = (float) dLog;
            d += dLog * dLog;
            i13++;
        }
        double dSqrt = Math.sqrt(d);
        if (dSqrt < 1.0E-8d) {
            return new float[24];
        }
        for (int i15 = 0; i15 < 24; i15++) {
            fArr8[i15] = (float) (((double) fArr8[i15]) / dSqrt);
        }
        return fArr8;
    }

    public final boolean matches(float[] live, List<float[]> wakes, List<float[]> rejects, int sensitivity) {
        Double dValueOf;
        Intrinsics.checkNotNullParameter(live, "live");
        Intrinsics.checkNotNullParameter(wakes, "wakes");
        Intrinsics.checkNotNullParameter(rejects, "rejects");
        if (wakes.size() < 3) {
            return true;
        }
        List<float[]> list = wakes;
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(list, 10));
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            arrayList.add(Double.valueOf(INSTANCE.cosine(live, (float[]) it.next())));
        }
        double dAverageOfDouble = CollectionsKt.averageOfDouble(CollectionsKt.take(CollectionsKt.sortedDescending(arrayList), 3));
        Iterator<T> it2 = rejects.iterator();
        if (it2.hasNext()) {
            double dCosine = INSTANCE.cosine(live, (float[]) it2.next());
            while (it2.hasNext()) {
                dCosine = Math.max(dCosine, INSTANCE.cosine(live, (float[]) it2.next()));
            }
            dValueOf = Double.valueOf(dCosine);
        } else {
            dValueOf = null;
        }
        return dAverageOfDouble >= ((((double) RangesKt.coerceIn(sensitivity, 0, 100)) / 100.0d) * 0.35d) + 0.5d && dAverageOfDouble >= (dValueOf != null ? dValueOf.doubleValue() : 0.0d) + 0.03d;
    }

    private final double cosine(float[] a, float[] b) {
        int iMin = Math.min(a.length, b.length);
        double d = 0.0d;
        double d2 = 0.0d;
        double d3 = 0.0d;
        for (int i = 0; i < iMin; i++) {
            double d4 = a[i];
            double d5 = b[i];
            d2 += d4 * d5;
            d += d4 * d4;
            d3 += d5 * d5;
        }
        if (d < 1.0E-8d || d3 < 1.0E-8d) {
            return 0.0d;
        }
        return d2 / Math.sqrt(d * d3);
    }

    private final float[] loudest(float[] samples, int sampleRate, double seconds) {
        double d = sampleRate;
        int iMax = Math.max(512, (int) (seconds * d));
        if (samples.length <= iMax) {
            return samples;
        }
        int iCoerceAtLeast = RangesKt.coerceAtLeast((int) (d * 0.08d), 1);
        int i = 0;
        double d2 = -1.0d;
        int i2 = 0;
        while (true) {
            int i3 = i + iMax;
            if (i3 > samples.length) {
                return ArraysKt.copyOfRange(samples, i2, iMax + i2);
            }
            double d3 = 0.0d;
            for (int i4 = i; i4 < i3; i4 += 8) {
                double d4 = samples[i4];
                d3 += d4 * d4;
            }
            if (d3 > d2) {
                i2 = i;
                d2 = d3;
            }
            i += iCoerceAtLeast;
        }
    }

    private final double hzToMel(double hz) {
        return Math.log10((hz / 700.0d) + 1.0d) * 2595.0d;
    }

    private final double melToHz(double mel) {
        return (pow(10.0d, mel / 2595.0d) - 1.0d) * 700.0d;
    }

    private final double pow(double d, double d2) {
        return Math.pow(d, d2);
    }

    private final void fft(float[] re, float[] im) {
        int length = re.length;
        int i = 0;
        for (int i2 = 1; i2 < length; i2++) {
            int i3 = length >> 1;
            while ((i & i3) != 0) {
                i ^= i3;
                i3 >>= 1;
            }
            i ^= i3;
            if (i2 < i) {
                float f = re[i2];
                re[i2] = re[i];
                re[i] = f;
                float f2 = im[i2];
                im[i2] = im[i];
                im[i] = f2;
            }
        }
        int i4 = 2;
        while (i4 <= length) {
            double d = (-6.283185307179586d) / ((double) i4);
            double dCos = Math.cos(d);
            double dSin = Math.sin(d);
            int i5 = 0;
            while (i5 < length) {
                int i6 = i4 >> 1;
                double d2 = 1.0d;
                double d3 = 0.0d;
                int i7 = 0;
                while (i7 < i6) {
                    int i8 = i5 + i7;
                    int i9 = i8 + i6;
                    float f3 = re[i9];
                    int i10 = length;
                    int i11 = i6;
                    float f4 = im[i9];
                    double d4 = dSin;
                    double d5 = (((double) f3) * d2) - (((double) f4) * d3);
                    int i12 = i5;
                    double d6 = (((double) f3) * d3) + (((double) f4) * d2);
                    float f5 = re[i8];
                    float f6 = im[i8];
                    double d7 = d3;
                    double d8 = f5;
                    double d9 = dCos;
                    re[i8] = (float) (d8 + d5);
                    double d10 = f6;
                    im[i8] = (float) (d10 + d6);
                    re[i9] = (float) (d8 - d5);
                    im[i9] = (float) (d10 - d6);
                    d3 = (d7 * d9) + (d2 * d4);
                    i7++;
                    d2 = (d2 * d9) - (d7 * d4);
                    i5 = i12;
                    length = i10;
                    i4 = i4;
                    i6 = i11;
                    dSin = d4;
                    dCos = d9;
                }
                i5 += i4;
                dCos = dCos;
            }
            i4 <<= 1;
        }
    }
}
