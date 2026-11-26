package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Regex;
import kotlin.text.StringsKt;

/* JADX INFO: compiled from: Phrase.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\"\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u0007\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\u0006J\u0018\u0010\t\u001a\u0004\u0018\u00010\u00062\u0006\u0010\n\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fJ\u0016\u0010\r\u001a\u00020\u00062\u0006\u0010\n\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fJ\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u0018\u0010\u0011\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u0018\u0010\u0013\u001a\u00020\f2\u0006\u0010\u0014\u001a\u00020\u00062\u0006\u0010\u0015\u001a\u00020\u0006H\u0002R\u0014\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u0016"}, d2 = {"Lcom/zippy/ear/Phrase;", "", "<init>", "()V", "wakeExact", "", "", "normalize", "value", "parseWake", "transcript", "sensitivity", "", "stripLeadingWake", "isWakePhrase", "", "phrase", "isBareZippy", "word", "levenshtein", "a", "b", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Phrase {
    public static final Phrase INSTANCE = new Phrase();
    private static final Set<String> wakeExact = SetsKt.setOf((Object[]) new String[]{"hey zippy", "hey zippi", "hey zippee", "hey sippy"});

    private Phrase() {
    }

    public final String normalize(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        String lowerCase = value.toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)");
        return StringsKt.trim((CharSequence) new Regex("\\s+").replace(new Regex("[^a-z\\s]").replace(lowerCase, " "), " ")).toString();
    }

    public final String parseWake(String transcript, int sensitivity) {
        Intrinsics.checkNotNullParameter(transcript, "transcript");
        List listSplit$default = StringsKt.split$default((CharSequence) normalize(transcript), new String[]{" "}, false, 0, 6, (Object) null);
        ArrayList arrayList = new ArrayList();
        for (Object obj : listSplit$default) {
            if (((String) obj).length() > 0) {
                arrayList.add(obj);
            }
        }
        ArrayList arrayList2 = arrayList;
        if (arrayList2.isEmpty()) {
            return null;
        }
        if (arrayList2.size() >= 2) {
            ArrayList arrayList3 = arrayList2;
            if (isWakePhrase(CollectionsKt.joinToString$default(CollectionsKt.take(arrayList3, 2), " ", null, null, 0, null, null, 62, null), sensitivity)) {
                return CollectionsKt.joinToString$default(CollectionsKt.drop(arrayList3, 2), " ", null, null, 0, null, null, 62, null);
            }
        }
        if (isBareZippy((String) arrayList2.get(0), sensitivity)) {
            return CollectionsKt.joinToString$default(CollectionsKt.drop(arrayList2, 1), " ", null, null, 0, null, null, 62, null);
        }
        return null;
    }

    public final String stripLeadingWake(String transcript, int sensitivity) {
        Intrinsics.checkNotNullParameter(transcript, "transcript");
        String wake = parseWake(transcript, sensitivity);
        return wake == null ? normalize(transcript) : wake;
    }

    private final boolean isWakePhrase(String phrase, int sensitivity) {
        if (wakeExact.contains(phrase)) {
            return true;
        }
        if (sensitivity <= 70 && Intrinsics.areEqual(phrase, "hey zipy")) {
            return true;
        }
        if (sensitivity <= 45 && (Intrinsics.areEqual(phrase, "hey zip") || Intrinsics.areEqual(phrase, "a zippy"))) {
            return true;
        }
        int iLevenshtein = levenshtein(phrase, "hey zippy");
        if (sensitivity > 55 || iLevenshtein != 1) {
            return sensitivity <= 25 && iLevenshtein <= 2;
        }
        return true;
    }

    private final boolean isBareZippy(String word, int sensitivity) {
        if (sensitivity > 15) {
            return false;
        }
        return Intrinsics.areEqual(word, Updates.REPO) || Intrinsics.areEqual(word, "zippi") || levenshtein(word, Updates.REPO) <= 1;
    }

    private final int levenshtein(String a, String b) {
        if (Intrinsics.areEqual(a, b)) {
            return 0;
        }
        if (a.length() == 0) {
            return b.length();
        }
        if (b.length() == 0) {
            return a.length();
        }
        int length = b.length() + 1;
        int[] iArr = new int[length];
        for (int i = 0; i < length; i++) {
            iArr[i] = i;
        }
        int length2 = a.length();
        if (1 <= length2) {
            int i2 = 1;
            while (true) {
                int i3 = i2 - 1;
                iArr[0] = i2;
                int length3 = b.length();
                if (1 <= length3) {
                    int i4 = 1;
                    int i5 = i3;
                    while (true) {
                        int i6 = iArr[i4];
                        int i7 = i4 - 1;
                        iArr[i4] = Math.min(iArr[i4] + 1, Math.min(iArr[i7] + 1, i5 + (a.charAt(i3) == b.charAt(i7) ? 0 : 1)));
                        if (i4 == length3) {
                            break;
                        }
                        i4++;
                        i5 = i6;
                    }
                }
                if (i2 == length2) {
                    break;
                }
                i2++;
            }
        }
        return iArr[b.length()];
    }
}
