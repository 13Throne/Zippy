package com.zippy.ear;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: Store.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u001f\b\u0086\b\u0018\u00002\u00020\u0001BO\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0006\u0012\u0006\u0010\b\u001a\u00020\u0006\u0012\u0006\u0010\t\u001a\u00020\u0006\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\u0003\u0012\u0006\u0010\r\u001a\u00020\u0003¢\u0006\u0004\b\u000e\u0010\u000fJ\t\u0010\u001c\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001d\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001e\u001a\u00020\u0006HÆ\u0003J\t\u0010\u001f\u001a\u00020\u0006HÆ\u0003J\t\u0010 \u001a\u00020\u0006HÆ\u0003J\t\u0010!\u001a\u00020\u0006HÆ\u0003J\t\u0010\"\u001a\u00020\u000bHÆ\u0003J\t\u0010#\u001a\u00020\u0003HÆ\u0003J\t\u0010$\u001a\u00020\u0003HÆ\u0003Jc\u0010%\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u00062\b\b\u0002\u0010\b\u001a\u00020\u00062\b\b\u0002\u0010\t\u001a\u00020\u00062\b\b\u0002\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\f\u001a\u00020\u00032\b\b\u0002\u0010\r\u001a\u00020\u0003HÆ\u0001J\u0013\u0010&\u001a\u00020\u000b2\b\u0010'\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010(\u001a\u00020\u0006HÖ\u0001J\t\u0010)\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0011R\u0011\u0010\u0005\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0007\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0014R\u0011\u0010\b\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0014R\u0011\u0010\t\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0014R\u0011\u0010\n\u001a\u00020\u000b¢\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u0011\u0010\f\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0011R\u0011\u0010\r\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u0011¨\u0006*"}, d2 = {"Lcom/zippy/ear/Settings;", "", "webhookUrl", "", "webhookKey", "sensitivity", "", "waitMs", "readyCue", "sentCue", "listening", "", "note", "heard", "<init>", "(Ljava/lang/String;Ljava/lang/String;IIIIZLjava/lang/String;Ljava/lang/String;)V", "getWebhookUrl", "()Ljava/lang/String;", "getWebhookKey", "getSensitivity", "()I", "getWaitMs", "getReadyCue", "getSentCue", "getListening", "()Z", "getNote", "getHeard", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final /* data */ class Settings {
    private final String heard;
    private final boolean listening;
    private final String note;
    private final int readyCue;
    private final int sensitivity;
    private final int sentCue;
    private final int waitMs;
    private final String webhookKey;
    private final String webhookUrl;

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final String getWebhookUrl() {
        return this.webhookUrl;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final String getWebhookKey() {
        return this.webhookKey;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final int getSensitivity() {
        return this.sensitivity;
    }

    /* JADX INFO: renamed from: component4, reason: from getter */
    public final int getWaitMs() {
        return this.waitMs;
    }

    /* JADX INFO: renamed from: component5, reason: from getter */
    public final int getReadyCue() {
        return this.readyCue;
    }

    /* JADX INFO: renamed from: component6, reason: from getter */
    public final int getSentCue() {
        return this.sentCue;
    }

    /* JADX INFO: renamed from: component7, reason: from getter */
    public final boolean getListening() {
        return this.listening;
    }

    /* JADX INFO: renamed from: component8, reason: from getter */
    public final String getNote() {
        return this.note;
    }

    /* JADX INFO: renamed from: component9, reason: from getter */
    public final String getHeard() {
        return this.heard;
    }

    public final Settings copy(String webhookUrl, String webhookKey, int sensitivity, int waitMs, int readyCue, int sentCue, boolean listening, String note, String heard) {
        Intrinsics.checkNotNullParameter(webhookUrl, "webhookUrl");
        Intrinsics.checkNotNullParameter(webhookKey, "webhookKey");
        Intrinsics.checkNotNullParameter(note, "note");
        Intrinsics.checkNotNullParameter(heard, "heard");
        return new Settings(webhookUrl, webhookKey, sensitivity, waitMs, readyCue, sentCue, listening, note, heard);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Settings)) {
            return false;
        }
        Settings settings = (Settings) other;
        return Intrinsics.areEqual(this.webhookUrl, settings.webhookUrl) && Intrinsics.areEqual(this.webhookKey, settings.webhookKey) && this.sensitivity == settings.sensitivity && this.waitMs == settings.waitMs && this.readyCue == settings.readyCue && this.sentCue == settings.sentCue && this.listening == settings.listening && Intrinsics.areEqual(this.note, settings.note) && Intrinsics.areEqual(this.heard, settings.heard);
    }

    public int hashCode() {
        return (((((((((((((((this.webhookUrl.hashCode() * 31) + this.webhookKey.hashCode()) * 31) + Integer.hashCode(this.sensitivity)) * 31) + Integer.hashCode(this.waitMs)) * 31) + Integer.hashCode(this.readyCue)) * 31) + Integer.hashCode(this.sentCue)) * 31) + Boolean.hashCode(this.listening)) * 31) + this.note.hashCode()) * 31) + this.heard.hashCode();
    }

    public String toString() {
        return "Settings(webhookUrl=" + this.webhookUrl + ", webhookKey=" + this.webhookKey + ", sensitivity=" + this.sensitivity + ", waitMs=" + this.waitMs + ", readyCue=" + this.readyCue + ", sentCue=" + this.sentCue + ", listening=" + this.listening + ", note=" + this.note + ", heard=" + this.heard + ")";
    }

    public Settings(String webhookUrl, String webhookKey, int i, int i2, int i3, int i4, boolean z, String note, String heard) {
        Intrinsics.checkNotNullParameter(webhookUrl, "webhookUrl");
        Intrinsics.checkNotNullParameter(webhookKey, "webhookKey");
        Intrinsics.checkNotNullParameter(note, "note");
        Intrinsics.checkNotNullParameter(heard, "heard");
        this.webhookUrl = webhookUrl;
        this.webhookKey = webhookKey;
        this.sensitivity = i;
        this.waitMs = i2;
        this.readyCue = i3;
        this.sentCue = i4;
        this.listening = z;
        this.note = note;
        this.heard = heard;
    }

    public final String getWebhookUrl() {
        return this.webhookUrl;
    }

    public final String getWebhookKey() {
        return this.webhookKey;
    }

    public final int getSensitivity() {
        return this.sensitivity;
    }

    public final int getWaitMs() {
        return this.waitMs;
    }

    public final int getReadyCue() {
        return this.readyCue;
    }

    public final int getSentCue() {
        return this.sentCue;
    }

    public final boolean getListening() {
        return this.listening;
    }

    public final String getNote() {
        return this.note;
    }

    public final String getHeard() {
        return this.heard;
    }
}
