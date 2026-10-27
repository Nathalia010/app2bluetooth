# Run with Windows PowerShell 5.1. Uses a local Spanish Windows voice; no network service.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $projectRoot '.tools'
$resourceRoot = Join-Path $projectRoot 'app\src\main\res\raw'
New-Item -ItemType Directory -Force $tempRoot, $resourceRoot | Out-Null
Add-Type -AssemblyName System.Speech
$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $speech.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq 'es' } | Select-Object -First 1
    if ($null -eq $voice) { throw 'Se necesita una voz de Windows en español para generar la palabra prueba.' }
    $speech.SelectVoice($voice.VoiceInfo.Name)
    $speech.Rate = -1
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $voicePath = Join-Path $tempRoot 'prueba-voice.wav'
    $speech.SetOutputToWaveFile($voicePath, $format)
    $speech.Speak('Prueba')
    $speech.SetOutputToNull()
} finally { $speech.Dispose() }

Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;

public static class TestAudioGenerator {
    const int Rate = 22050;
    static double Hz(int midi) { return 440 * Math.Pow(2, (midi - 69) / 12.0); }
    static void Note(float[] mix, double start, double length, int midi, double volume, bool bass) {
        int first = (int)(start * Rate), count = (int)(length * Rate);
        double hz = Hz(midi);
        for (int n = 0; n < count && first + n < mix.Length; n++) {
            double t = n / (double)Rate;
            double attack = Math.Min(1, t / 0.012);
            double release = Math.Min(1, (length - t) / 0.12);
            double envelope = attack * release * Math.Exp(-t * (bass ? 2.2 : 2.7));
            double phase = 2 * Math.PI * hz * t;
            double tone = Math.Sin(phase) + (bass ? 0.18 : 0.26) * Math.Sin(2 * phase) + 0.07 * Math.Sin(3 * phase);
            mix[first + n] += (float)(volume * envelope * tone);
        }
    }
    static short[] Voice(string path) {
        using (var reader = new BinaryReader(File.OpenRead(path))) {
            if (Encoding.ASCII.GetString(reader.ReadBytes(4)) != "RIFF") throw new Exception("Invalid voice WAV");
            reader.ReadInt32();
            if (Encoding.ASCII.GetString(reader.ReadBytes(4)) != "WAVE") throw new Exception("Invalid voice WAV");
            while (reader.BaseStream.Position + 8 <= reader.BaseStream.Length) {
                string chunk = Encoding.ASCII.GetString(reader.ReadBytes(4));
                int size = reader.ReadInt32();
                long end = reader.BaseStream.Position + size + (size & 1);
                if (chunk == "fmt ") {
                    if (reader.ReadInt16() != 1 || reader.ReadInt16() != 1 || reader.ReadInt32() != Rate)
                        throw new Exception("Voice must be 22050 Hz mono PCM");
                    reader.ReadInt32(); reader.ReadInt16();
                    if (reader.ReadInt16() != 16) throw new Exception("Voice must be 16 bit PCM");
                } else if (chunk == "data") {
                    short[] data = new short[size / 2];
                    for (int i = 0; i < data.Length; i++) data[i] = reader.ReadInt16();
                    return data;
                }
                reader.BaseStream.Position = end;
            }
        }
        throw new Exception("Voice WAV has no samples");
    }
    public static void Generate(string voicePath, string output) {
        // Original light lounge arrangement: soft electric piano, bass and brushed rhythm.
        float[] music = new float[34 * Rate];
        double beat = 60.0 / 96;
        int[][] chords = { new[]{60,64,67,71}, new[]{57,60,64,67}, new[]{62,65,69,72}, new[]{55,59,62,65} };
        int[] melody = {76,79,78,74,72,76,74,71,74,77,76,72,71,74,72,67};
        for (int bar = 0; bar < 14; bar++) {
            double start = bar * 4 * beat;
            int[] chord = chords[bar % 4];
            foreach (double offset in new[]{0.0, 1.5, 3.0})
                foreach (int midi in chord) Note(music, start + offset * beat, 1.1, midi, 0.04, false);
            Note(music, start, 1.0, chord[0] - 24, 0.13, true);
            Note(music, start + 2 * beat, 0.95, chord[0] - 17, 0.10, true);
            Note(music, start + beat, 0.7, melody[(bar * 2) % melody.Length], 0.07, false);
            Note(music, start + 2.5 * beat, 0.75, melody[(bar * 2 + 1) % melody.Length], 0.065, false);
        }
        Random random = new Random(21);
        for (double start = 0; start < 34; start += beat / 2) {
            int first = (int)(start * Rate);
            double previous = 0;
            for (int n = 0; n < Rate / 12 && first + n < music.Length; n++) {
                double noise = random.NextDouble() * 2 - 1;
                double brushed = noise - previous; previous = noise;
                music[first + n] += (float)(0.009 * brushed * Math.Exp(-n / (Rate * 0.025)));
            }
        }
        short[] voice = Voice(voicePath);
        foreach (double start in new[]{0.45, 16.45}) {
            int first = (int)(start * Rate);
            for (int n = -Rate / 10; n < voice.Length + Rate / 5; n++) {
                int at = first + n;
                if (at >= 0 && at < music.Length) music[at] *= 0.27f;
            }
            for (int n = 0; n < voice.Length && first + n < music.Length; n++)
                music[first + n] += (float)(0.90 * voice[n] / 32768.0);
        }
        double peak = 0;
        for (int i = 0; i < music.Length; i++) {
            double fade = Math.Min(1, i / (Rate * 0.18)) * Math.Min(1, (music.Length - i) / (Rate * 1.4));
            music[i] *= (float)fade;
            peak = Math.Max(peak, Math.Abs(music[i]));
        }
        double gain = peak > 0 ? 0.82 / peak : 1;
        using (var writer = new BinaryWriter(File.Create(output))) {
            writer.Write(Encoding.ASCII.GetBytes("RIFF")); writer.Write(36 + music.Length * 2);
            writer.Write(Encoding.ASCII.GetBytes("WAVEfmt ")); writer.Write(16);
            writer.Write((short)1); writer.Write((short)1); writer.Write(Rate); writer.Write(Rate * 2);
            writer.Write((short)2); writer.Write((short)16);
            writer.Write(Encoding.ASCII.GetBytes("data")); writer.Write(music.Length * 2);
            foreach (float sample in music) writer.Write((short)Math.Round(sample * gain * 32767));
        }
    }
}
'@
$output = Join-Path $resourceRoot 'prueba_ascensor.wav'
[TestAudioGenerator]::Generate($voicePath, $output)
Write-Output "Audio creado: $output (34 segundos, palabra prueba y musica original)."
