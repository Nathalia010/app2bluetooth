using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;
using System.Xml.Linq;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace JuntosDesktop
{
    sealed class Preferences
    {
        readonly string path;
        public bool Dark;
        public string[] Devices = { "", "" };
        public Preferences(string path) { this.path = path; }
        public void Load()
        {
            try {
                if (!File.Exists(path)) return;
                XElement data = XElement.Load(path);
                Dark = (string)data.Attribute("dark") == "true";
                for (int i = 0; i < 2; i++) Devices[i] = (string)data.Element("device" + i) ?? "";
            } catch { /* Corrupt preferences must not prevent startup. */ }
        }
        public void Save()
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            new XElement("juntos", new XAttribute("dark", Dark ? "true" : "false"),
                new XElement("device0", Devices[0]), new XElement("device1", Devices[1])).Save(path);
        }
    }

    sealed class Output : IDisposable
    {
        public string Id, Name;
        public MMDevice Device;
        public override string ToString() { return Name; }
        public void Dispose() { if (Device != null) Device.Dispose(); }
    }

    // A single, explicitly chosen render endpoint is captured; never capture the destination.
    sealed class AudioShare : IDisposable
    {
        readonly object gate = new object();
        WasapiLoopbackCapture capture;
        WasapiOut playback;
        BufferedWaveProvider buffer;
        bool stopping;
        public event Action<string> Failed;
        public bool Running { get { return capture != null && !stopping; } }
        public static bool CanShare(string source, string destination)
        { return !String.IsNullOrEmpty(source) && !String.IsNullOrEmpty(destination) && source != destination; }
        public static void Append(BufferedWaveProvider buffer, byte[] data, int count)
        {
            // Discard an old backlog rather than replaying delayed sound after a stall.
            if (buffer.BufferedDuration.TotalMilliseconds > 350) buffer.ClearBuffer();
            buffer.AddSamples(data, 0, count);
        }
        public void Start(Output source, Output destination)
        {
            if (!CanShare(source.Id, destination.Id)) throw new InvalidOperationException("Elige dos salidas distintas.");
            Dispose(); stopping = false;
            try {
                capture = new WasapiLoopbackCapture(source.Device);
                buffer = new BufferedWaveProvider(capture.WaveFormat) {
                    BufferDuration = TimeSpan.FromSeconds(1), DiscardOnBufferOverflow = true, ReadFully = true
                };
                playback = new WasapiOut(destination.Device, AudioClientShareMode.Shared, true, 120);
                playback.Init(buffer);
                capture.DataAvailable += delegate(object sender, WaveInEventArgs e) {
                    lock (gate) { if (!stopping && buffer != null) Append(buffer, e.Buffer, e.BytesRecorded); }
                };
                capture.RecordingStopped += delegate(object sender, StoppedEventArgs e) {
                    if (!stopping && e.Exception != null && Failed != null) Failed("Se interrumpió la captura: " + e.Exception.Message);
                };
                playback.PlaybackStopped += delegate(object sender, StoppedEventArgs e) {
                    if (!stopping && e.Exception != null && Failed != null) Failed("Se interrumpió la salida: " + e.Exception.Message);
                };
                playback.Play(); capture.StartRecording();
            } catch { Dispose(); throw; }
        }
        public void Dispose()
        {
            stopping = true;
            if (capture != null) { try { capture.StopRecording(); } catch { } capture.Dispose(); capture = null; }
            if (playback != null) { try { playback.Stop(); } catch { } playback.Dispose(); playback = null; }
            lock (gate) { buffer = null; }
        }
    }

    sealed class MainWindow : Form
    {
        readonly Preferences preferences;
        readonly bool demo;
        readonly List<Output> outputs = new List<Output>();
        readonly ComboBox[] choices = new ComboBox[2];
        readonly CheckBox[] enabled = new CheckBox[2];
        readonly TrackBar[] volumes = new TrackBar[2];
        readonly Label[] levels = new Label[2];
        readonly Label[] states = new Label[2];
        readonly AudioShare share = new AudioShare();
        readonly Timer timer = new Timer();
        readonly Label status = new Label();
        readonly Button theme = new Button(), sharing = new Button();
        readonly FlowLayoutPanel root = new FlowLayoutPanel();
        readonly Panel volumePanel = new Panel();
        readonly NotifyIcon tray = new NotifyIcon();
        bool syncing, closing;
        string topology = "";
        int ticks;

        public MainWindow(bool demo, string settingsPath)
        {
            this.demo = demo; preferences = new Preferences(settingsPath); preferences.Load();
            Text = "Juntos Bluetooth"; Size = new Size(650, 760); MinimumSize = new Size(530, 600);
            StartPosition = FormStartPosition.CenterScreen; Font = new Font("Segoe UI", 10);
            AutoScaleMode = AutoScaleMode.Dpi; Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;
            root.Dock = DockStyle.Fill; root.FlowDirection = FlowDirection.TopDown; root.WrapContents = false;
            root.AutoScroll = true; root.Padding = new Padding(24, 16, 24, 20); Controls.Add(root);
            Panel header = new Panel { Height = 54, Width = 560 };
            Label title = new Label { Text = "JUNTOS BLUETOOTH", Font = new Font("Segoe UI", 12, FontStyle.Bold), AutoSize = true, Location = new Point(0, 15) };
            header.Controls.Add(title); theme.Text = "☾"; theme.Size = new Size(50, 44); theme.Dock = DockStyle.Right;
            theme.AccessibleName = "Cambiar tema"; theme.Click += delegate { preferences.Dark = !preferences.Dark; Save(); ApplyTheme(this); }; header.Controls.Add(theme); root.Controls.Add(header);
            AddLabel("Dos salidas, tu sonido", 19, true, 42);
            AddLabel("Conecta tus audífonos en Bluetooth y elige sus salidas de audio.", 10, false, 36);
            for (int i = 0; i < 2; i++) {
                int index = i;
                Panel card = new Panel { Height = 106, Width = 560, Padding = new Padding(12), Tag = "card" };
                Label caption = new Label { Text = "DISPOSITIVO " + (i + 1), AutoSize = true, Location = new Point(12, 10), Font = new Font("Segoe UI", 9, FontStyle.Bold) };
                card.Controls.Add(caption);
                choices[i] = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Location = new Point(12, 36), Width = 420, Anchor = AnchorStyles.Left | AnchorStyles.Top | AnchorStyles.Right };
                choices[i].AccessibleName = "Salida de audio " + (i + 1); choices[i].SelectedIndexChanged += delegate { SelectionChanged(index); }; card.Controls.Add(choices[i]);
                enabled[i] = new CheckBox { Text = "Sonar", AutoSize = true, Location = new Point(458, 38), Anchor = AnchorStyles.Right | AnchorStyles.Top };
                enabled[i].AccessibleName = "Activar sonido del dispositivo " + (i + 1); enabled[i].CheckedChanged += delegate { Toggle(index); }; card.Controls.Add(enabled[i]);
                states[i] = new Label { Text = "Sin salida seleccionada", Location = new Point(12, 74), AutoSize = true }; card.Controls.Add(states[i]); root.Controls.Add(card);
            }
            Button fold = AddButton("Volumen por dispositivo  ▾", delegate(object sender, EventArgs e) {
                volumePanel.Visible = !volumePanel.Visible;
                ((Button)sender).Text = "Volumen por dispositivo  " + (volumePanel.Visible ? "▴" : "▾");
            });
            volumePanel.Size = new Size(560, 94); volumePanel.Visible = false;
            for (int i = 0; i < 2; i++) {
                int index = i;
                Label caption = new Label { Text = (i + 1).ToString(), Location = new Point(6, 12 + i * 40), Width = 18 }; volumePanel.Controls.Add(caption);
                volumes[i] = new TrackBar { Minimum = 0, Maximum = 100, TickStyle = TickStyle.None, Location = new Point(28, i * 40), Width = 440, Height = 36, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
                volumes[i].AccessibleName = "Volumen del dispositivo " + (i + 1); volumes[i].Scroll += delegate { SetVolume(index); }; volumePanel.Controls.Add(volumes[i]);
                levels[i] = new Label { Text = "—", Location = new Point(482, 10 + i * 40), Width = 65, Anchor = AnchorStyles.Right | AnchorStyles.Top }; volumePanel.Controls.Add(levels[i]);
            }
            root.Controls.Add(volumePanel);
            sharing.Text = "Compartir audio del 1 al 2"; sharing.Height = 44; sharing.Width = 560;
            sharing.Click += delegate { ToggleShare(); }; root.Controls.Add(sharing);
            AddLabel("Reproduce tu música por el dispositivo 1. Compartir envía una copia al 2 mientras Juntos permanece abierto. Puede haber retraso entre audífonos.", 10, false, 62);
            AddButton("＋ Abrir configuración de Bluetooth", delegate { Open("ms-settings:bluetooth"); });
            AddButton("Salidas de sonido de Windows", delegate { Open("ms-settings:sound"); });
            AddButton("Actualizar dispositivos", delegate { RefreshOutputs(true); });
            AddButton("Minimizar a la bandeja", delegate { Hide(); tray.Visible = true; });
            status.AutoSize = false; status.Size = new Size(560, 58); status.Text = "Listo. Selecciona dos salidas diferentes."; root.Controls.Add(status);
            AddButton("Hecho con cariño · Créditos", delegate {
                MessageBox.Show(this, "Juntos Bluetooth para Windows 0.1.1\n\nHecho por Manuel Martinez y Karen Martinez\n© 2026. Todos los derechos reservados.\n\nAudio: NAudio, Mark Heath y colaboradores (licencia MIT incluida).", "Créditos", MessageBoxButtons.OK, MessageBoxIcon.Information);
            });
            root.SizeChanged += delegate { foreach (Control child in root.Controls) child.Width = Math.Max(450, root.ClientSize.Width - 64); };
            ApplyTheme(this);
            ContextMenuStrip menu = new ContextMenuStrip(); menu.Items.Add("Abrir Juntos", null, delegate { Show(); WindowState = FormWindowState.Normal; Activate(); });
            menu.Items.Add("Detener audio compartido", null, delegate { StopSharing(); }); menu.Items.Add("Salir", null, delegate { Close(); });
            tray.Icon = Icon; tray.Text = "Juntos Bluetooth"; tray.ContextMenuStrip = menu;
            tray.DoubleClick += delegate { Show(); WindowState = FormWindowState.Normal; Activate(); };
            share.Failed += delegate(string message) { if (!closing && IsHandleCreated) BeginInvoke((Action)delegate { StopSharing(); status.Text = message; }); };
            if (demo) {
                outputs.Add(new Output { Id = "demo1", Name = "Audífonos pequeños" }); outputs.Add(new Output { Id = "demo2", Name = "Diadema Bluetooth" });
                preferences.Devices[0] = "demo1"; preferences.Devices[1] = "demo2"; FillChoices();
            } else { RefreshOutputs(true); timer.Interval = 700; timer.Tick += delegate { RefreshLevels(); if (++ticks % 5 == 0) RefreshOutputs(false); }; timer.Start(); }
            FormClosing += delegate { closing = true; timer.Stop(); share.Dispose(); tray.Dispose(); foreach (Output output in outputs) output.Dispose(); Save(); };
        }
        void AddLabel(string text, int size, bool bold, int height)
        { root.Controls.Add(new Label { Text = text, Width = 560, Height = height, Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular) }); }
        Button AddButton(string text, EventHandler click)
        { Button b = new Button { Text = text, Width = 560, Height = 40, FlatStyle = FlatStyle.Flat }; b.Click += click; root.Controls.Add(b); return b; }
        void Save() { if (demo) return; try { preferences.Save(); } catch { status.Text = "No se pudieron guardar las preferencias."; } }
        void Open(string uri) { try { Process.Start(new ProcessStartInfo(uri) { UseShellExecute = true }); } catch (Exception ex) { status.Text = "No se pudo abrir Ajustes: " + ex.Message; } }
        void ApplyTheme(Control parent)
        {
            Color bg = preferences.Dark ? Color.FromArgb(19, 30, 25) : Color.FromArgb(237, 246, 239);
            Color fg = preferences.Dark ? Color.FromArgb(232, 245, 237) : Color.FromArgb(27, 52, 37);
            parent.BackColor = parent.Tag as string == "card" ? (preferences.Dark ? Color.FromArgb(32, 48, 40) : Color.White) : bg;
            if (parent is Label || parent is CheckBox) parent.BackColor = Color.Transparent;
            parent.ForeColor = fg;
            foreach (Control child in parent.Controls) ApplyTheme(child);
            theme.Text = preferences.Dark ? "☀" : "☾";
        }
        Output Selected(int i) { return choices[i].SelectedItem as Output; }
        void FillChoices()
        {
            syncing = true;
            for (int i = 0; i < 2; i++) {
                choices[i].Items.Clear(); choices[i].Items.Add("Seleccionar salida de audio…");
                foreach (Output output in outputs) choices[i].Items.Add(output);
                Output saved = outputs.FirstOrDefault(o => o.Id == preferences.Devices[i]);
                choices[i].SelectedItem = saved == null ? (object)choices[i].Items[0] : saved;
            }
            syncing = false; RefreshLevels();
        }
        void SelectionChanged(int index)
        {
            if (syncing) return;
            StopSharing(); Output output = Selected(index), other = Selected(1 - index);
            if (output != null && other != null && output.Id == other.Id) {
                syncing = true; choices[index].SelectedIndex = 0; syncing = false;
                status.Text = "Cada posición debe usar una salida diferente."; output = null;
            }
            preferences.Devices[index] = output == null ? "" : output.Id; Save(); RefreshLevels();
        }
        void RefreshOutputs(bool force)
        {
            if (demo) return;
            List<Output> fresh = new List<Output>();
            try {
                using (MMDeviceEnumerator enumerator = new MMDeviceEnumerator()) {
                    foreach (MMDevice device in enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active))
                        fresh.Add(new Output { Id = device.ID, Name = device.FriendlyName, Device = device });
                }
                string current = String.Join("|", fresh.Select(o => o.Id).OrderBy(id => id));
                if (!force && current == topology) { foreach (Output o in fresh) o.Dispose(); return; }
                StopSharing(); foreach (Output o in outputs) o.Dispose(); outputs.Clear(); outputs.AddRange(fresh); topology = current;
                FillChoices();
                status.Text = outputs.Count == 0 ? "Windows no informa salidas disponibles. Conecta tus audífonos en Bluetooth." : "Salidas actualizadas. La lista incluye Bluetooth y otras salidas de Windows.";
            } catch (Exception ex) { foreach (Output o in fresh) if (!outputs.Contains(o)) o.Dispose(); status.Text = "No se pudo consultar el audio: " + ex.Message; }
        }
        void RefreshLevels()
        {
            syncing = true;
            for (int i = 0; i < 2; i++) {
                Output o = Selected(i); bool available = o != null;
                try {
                    bool muted = available && !demo && o.Device.AudioEndpointVolume.Mute;
                    int level = !available ? 0 : demo ? (i == 0 ? 65 : 40) : (int)Math.Round(o.Device.AudioEndpointVolume.MasterVolumeLevelScalar * 100);
                    enabled[i].Enabled = available; enabled[i].Checked = available && !muted;
                    volumes[i].Enabled = available && !muted; volumes[i].Value = muted ? 0 : Math.Max(0, Math.Min(100, level));
                    levels[i].Text = available ? (muted ? "0%" : level + "%") : "—";
                    states[i].Text = !available ? "Sin salida seleccionada" : muted ? "Silenciado · nivel guardado" : "Salida disponible en Windows";
                } catch { enabled[i].Enabled = volumes[i].Enabled = false; enabled[i].Checked = false; levels[i].Text = "—"; states[i].Text = "Salida desconectada"; }
            }
            syncing = false;
        }
        void Toggle(int index)
        {
            if (syncing || demo) return;
            try { Output output = Selected(index); if (output != null) output.Device.AudioEndpointVolume.Mute = !enabled[index].Checked; }
            catch (Exception ex) { status.Text = "No se pudo cambiar el sonido: " + ex.Message; }
            RefreshLevels();
        }
        void SetVolume(int index)
        {
            if (syncing || demo) return;
            try { Output output = Selected(index); if (output != null) output.Device.AudioEndpointVolume.MasterVolumeLevelScalar = volumes[index].Value / 100f; }
            catch (Exception ex) { status.Text = "No se pudo cambiar el volumen: " + ex.Message; }
            RefreshLevels();
        }
        void ToggleShare()
        {
            if (share.Running) { StopSharing(); return; }
            Output source = Selected(0), destination = Selected(1);
            if (source == null || destination == null) { status.Text = "Selecciona las dos salidas antes de compartir."; return; }
            if (demo) return;
            try { share.Start(source, destination); sharing.Text = "Detener audio compartido"; status.Text = "Compartiendo del 1 al 2. El audio se procesa localmente y no se guarda."; }
            catch (Exception ex) { status.Text = "No se pudo compartir: " + ex.Message; }
        }
        void StopSharing() { share.Dispose(); sharing.Text = "Compartir audio del 1 al 2"; status.Text = "Audio compartido detenido."; }

        public void RenderPreview(string path)
        {
            ShowInTaskbar = false; Opacity = 0; Show(); volumePanel.Visible = true;
            PerformLayout(); root.PerformLayout(); Application.DoEvents();
            using (Bitmap bitmap = new Bitmap(Width, Height)) { DrawToBitmap(bitmap, new Rectangle(Point.Empty, Size)); bitmap.Save(path); }
            Hide();
        }
    }

    static class Program
    {
        [STAThread] static int Main(string[] args)
        {
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
            string settings = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "JuntosBluetooth", "preferences.xml");
            if (args.Length > 0 && args[0] == "--self-test") {
                string directory = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "test-output"); Directory.CreateDirectory(directory);
                try {
                    if (AudioShare.CanShare("a", "a") || AudioShare.CanShare("", "b") || !AudioShare.CanShare("a", "b")) throw new Exception("Routing validation failed");
                    BufferedWaveProvider buffer = new BufferedWaveProvider(new WaveFormat(48000, 16, 2)) { BufferDuration = TimeSpan.FromSeconds(1), DiscardOnBufferOverflow = true };
                    byte[] first = Enumerable.Repeat((byte)7, 80000).ToArray(); AudioShare.Append(buffer, first, first.Length);
                    byte[] next = Enumerable.Repeat((byte)3, 400).ToArray(); AudioShare.Append(buffer, next, next.Length);
                    if (buffer.BufferedBytes != 400) throw new Exception("Backlog was not cleared");
                    byte[] read = new byte[400]; buffer.Read(read, 0, 400); if (read.Any(b => b != 3)) throw new Exception("Audio bytes changed");
                    string testSettings = Path.Combine(directory, "preferences.xml"); Preferences p = new Preferences(testSettings); p.Dark = true; p.Devices[0] = "exact-id-1"; p.Devices[1] = "exact-id-2"; p.Save();
                    Preferences loaded = new Preferences(testSettings); loaded.Load(); if (!loaded.Dark || loaded.Devices[1] != "exact-id-2") throw new Exception("Preferences roundtrip failed");
                    int available;
                    using (MMDeviceEnumerator enumerator = new MMDeviceEnumerator()) {
                        var endpoints = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);
                        available = endpoints.Count;
                        foreach (MMDevice endpoint in endpoints) endpoint.Dispose();
                    }
                    File.WriteAllText(Path.Combine(directory, "results.txt"), "PASS: routing identity, no self-loop, bounded audio buffer, PCM integrity, settings persistence.\nCore Audio enumeration succeeded: " + available + " active outputs.\nHardware playback not tested.");
                    return 0;
                } catch (Exception ex) { File.WriteAllText(Path.Combine(directory, "results.txt"), ex.ToString()); return 1; }
            }
            if (args.Length > 0 && args[0] == "--preview") {
                using (MainWindow form = new MainWindow(true, settings)) form.RenderPreview(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "preview.png")); return 0;
            }
            try { Application.Run(new MainWindow(false, settings)); return 0; }
            catch (Exception ex) { MessageBox.Show("Juntos no pudo iniciar: " + ex.Message, "Juntos Bluetooth"); return 1; }
        }
    }
}
