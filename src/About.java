package io.github.shumtugle.hark;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public final class About {

    public static final String VERSION = "2.9.7";
    public static final String SRC = "https://github.com/Shumtugle/hark";
    public static final String CLAUDE = "https://claude.ai";
    public static final String PAGE = "https://shumtugle.github.io/hark/";

    private About() {}

    private static AlertDialog.Builder dark(MainActivity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog);
    }

    private static TextView row(MainActivity a, String text, String note, int color) {
        LinearLayout box = new LinearLayout(a);
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(16f);
        t.setTextColor(color);
        t.setPadding(Ui.dp(a, 24), Ui.dp(a, 13), Ui.dp(a, 24), note == null ? Ui.dp(a, 13) : Ui.dp(a, 2));
        return t;
    }

    public static void menu(final MainActivity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.SURFACE);
        col.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
        final AlertDialog d = dark(a).setView(scroll(a, col)).create();

        TextView srt = row(a, I18n.t(I18n.SORT) + "  \u00B7  " + Folder.sortLabel(a.store().sortMode())
                + (a.store().sortDesc() ? "  \u2191" : "  \u2193"), null, Ui.TEXT);
        srt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); Sheets.sort(a); }
        });
        col.addView(srt);

        TextView his = row(a, I18n.t(I18n.HISTORY), null, Ui.TEXT);
        his.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); Sheets.history(a); }
        });
        col.addView(his);

        TextView fld = row(a, I18n.t(I18n.PICK), null, Ui.TEXT);
        fld.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.pickFolder(); }
        });
        col.addView(fld);

        col.addView(line(a));

        TextView set = row(a, I18n.t(I18n.SETTINGS), null, Ui.TEXT);
        set.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); settings(a); }
        });
        col.addView(set);

        TextView abt = row(a, I18n.t(I18n.ABOUT), null, Ui.ACCENT);
        abt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); doc(a); }
        });
        col.addView(abt);

        d.show();
    }

    public static void settings(final MainActivity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.SURFACE);
        col.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));

        final AlertDialog d = dark(a).setView(scroll(a, col)).create();

        TextView head = row(a, "Hark " + VERSION, null, Ui.TEXT_2);
        head.setTextSize(12f);
        col.addView(head);

        TextView lang = row(a, I18n.t(I18n.LANGUAGE) + "  ·  "
                + I18n.nameOf(a.store().lang()), null, Ui.TEXT);
        lang.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); language(a); }
        });
        col.addView(lang);

        TextView sp = row(a, I18n.t(I18n.SPEED) + "  ·  " + speedLabel(a.store().speed()), null, Ui.TEXT);
        sp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); Sheets.speed(a); }
        });
        col.addView(sp);

        final long[] steps = {0L, 2000L, 3500L, 5000L, 8000L};
        final TextView rb = row(a, "", null, Ui.TEXT);
        rb.setText(I18n.t(I18n.RESUME_BACK) + "  \u00B7  " + backLabel(a.store().resumeBack()));
        rb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                long cur = a.store().resumeBack();
                int at = 0;
                for (int i = 0; i < steps.length; i++) if (steps[i] == cur) at = i;
                long next = steps[(at + 1) % steps.length];
                a.store().resumeBack(next);
                rb.setText(I18n.t(I18n.RESUME_BACK) + "  \u00B7  " + backLabel(next));
            }
        });
        col.addView(rb);

        final TextView flt = row(a, "", null, Ui.TEXT);
        flt.setText(I18n.t(I18n.REMEMBER_FILTER) + "  ·  "
                + (a.store().rememberFilter() ? "\u2713" : "\u2014"));
        flt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                boolean v = !a.store().rememberFilter();
                a.store().rememberFilter(v);
                flt.setText(I18n.t(I18n.REMEMBER_FILTER) + "  ·  " + (v ? "\u2713" : "\u2014"));
            }
        });
        col.addView(flt);

        final String[] names = {"0 %", "25 %", "50 %", "75 %", "100 %"};
        final TextView wg = row(a, "", null, Ui.TEXT);
        wg.setText(I18n.t(I18n.WIDGET) + "  \u00B7  " + names[a.store().widgetShade()]);
        wg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                int v = (a.store().widgetShade() + 1) % names.length;
                a.store().widgetShade(v);
                wg.setText(I18n.t(I18n.WIDGET) + "  \u00B7  " + names[v]);
                Widget.push(a);
            }
        });
        col.addView(wg);

        TextView fld = row(a, I18n.t(I18n.PICK), null, Ui.TEXT);
        fld.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.pickFolder(); }
        });
        col.addView(fld);

        col.addView(line(a));

        TextView abt = row(a, I18n.t(I18n.ABOUT), null, Ui.ACCENT);
        abt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); doc(a); }
        });
        col.addView(abt);

        TextView prb = row(a, I18n.t(I18n.PROBE), null, Ui.TEXT_2);
        prb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); Sheets.probe(a); }
        });
        col.addView(prb);

        d.show();
    }

    private static String backLabel(long ms) {
        if (ms <= 0) return "\u2014";
        return (ms % 1000 == 0 ? String.valueOf(ms / 1000) : String.valueOf(ms / 1000.0)) + " s";
    }

    private static String speedLabel(float v) {
        return (v == 1.0f ? "1.0" : String.valueOf(v)) + "\u00D7";
    }

    private static View line(MainActivity a) {
        View v = new View(a);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 1));
        lp.topMargin = Ui.dp(a, 8);
        lp.bottomMargin = Ui.dp(a, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private static ScrollView scroll(MainActivity a, View v) {
        ScrollView s = new ScrollView(a);
        s.setBackgroundColor(Ui.SURFACE);
        s.addView(v);
        return s;
    }

    public static void language(final MainActivity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.SURFACE);
        col.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
        final AlertDialog d = dark(a).setView(scroll(a, col)).create();

        String cur = a.store().lang();
        for (int i = 0; i < I18n.CODES.length; i++) {
            final String code = I18n.CODES[i];
            boolean on = code.equals(cur);
            TextView t = row(a, I18n.FLAGS[i] + "   " + I18n.NAMES[i], null,
                    on ? Ui.ACCENT : Ui.TEXT);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    a.store().lang(code);
                    d.dismiss();
                    a.recreate();
                }
            });
            col.addView(t);
        }
        d.show();
    }

    public static void doc(final MainActivity a) {
        String body = ru() ? RU_DOC : EN_DOC;

        TextView t = new TextView(a);
        t.setText(Html.fromHtml(body, Html.FROM_HTML_MODE_COMPACT));
        t.setTextColor(Ui.TEXT);
        t.setLinkTextColor(Ui.ACCENT);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setTextSize(14f);
        t.setLineSpacing(Ui.dp(a, 3), 1f);
        t.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 10));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.SURFACE);
        col.addView(t);

        LinearLayout links = new LinearLayout(a);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER);
        links.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 16));
        links.addView(linkBtn(a, "claude.ai", CLAUDE));
        links.addView(linkBtn(a, "GitHub", SRC));
        col.addView(links);

        TextView ver = new TextView(a);
        ver.setText("generated by AI \u00B7 Claude Opus 5 \u00B7 v" + VERSION);
        ver.setTextColor(Ui.TEXT_OFF);
        ver.setTextSize(11f);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, 0, 0, Ui.dp(a, 16));
        col.addView(ver);

        dark(a).setView(scroll(a, col))
               .setPositiveButton(I18n.t(I18n.CLOSE), null)
               .show();
    }

    private static TextView linkBtn(final MainActivity a, String label, final String url) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(14f);
        t.setTextColor(Ui.ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 18), Ui.dp(a, 10), Ui.dp(a, 18), Ui.dp(a, 10));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(a, 18));
        g.setStroke(Ui.dp(a, 1), Ui.LINE);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 6); lp.rightMargin = Ui.dp(a, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { Probe.log("link did not open: " + url); }
            }
        });
        return t;
    }

    private static boolean ru() {
        String c = I18n.code();
        return c.equals("ru") || c.equals("uk") || c.equals("ky") || c.equals("kk");
    }

    private static final String RU_DOC =
        "<p><b>Hark " + VERSION + "</b></p>" +
        "<p>Проигрыватель длинных записей из одной папки. Не медиатека: Hark открывает " +
        "папку такой, какая она есть на диске, и не знает слов «исполнитель» и «альбом». " +
        "Код, вёрстка и логика полностью сгенерированы ИИ, без участия человека.</p>" +

        "<h3>Как он думает</h3>" +
        "<p><b>Позиция важнее всего.</b> Место в каждом файле запоминается отдельно, " +
        "не «последний трек», а каждый по своему documentId. Возврат откатывает на пять " +
        "секунд назад: вернувшись к лекции через неделю, вы услышите конец прошлой фразы, " +
        "а не середину слова.</p>" +
        "<p><b>Папка читается одним запросом.</b> Ни сканирования памяти, ни базы, ни индекса. " +
        "Длительность подтягивается лениво, по мере отрисовки строки, потому что открыть " +
        "метаданные трёхсот файлов разом — это несколько секунд немоты.</p>" +
        "<p><b>Двухчасовой файл ломает полосу прокрутки.</b> Один пиксель равен семи секундам, " +
        "точный переход перетаскиванием невозможен физически. Поэтому доступа три: кнопки " +
        "на пятнадцать и тридцать секунд, грубое перетаскивание по всей высоте панели и " +
        "точный ввод времени долгим тапом по цифрам.</p>" +

        "<h3>Шапка</h3>" +
        "<p>Стрелка возврата, имя папки, фильтр, звезда и логотип. Больше в шапке нет ничего: " +
        "всё, что нужно изредка, живёт за логотипом.</p>" +
        "<p><b>MP4</b> — фильтр. По умолчанию показано всё звуковое; тап сужает список до MP4-семейства, состояние запоминается. " +
        "Долгий тап открывает дознание: там видно, какой MIME отдала прошивка каждому файлу, " +
        "дали ли аудиофокус, сработали ли наушники.</p>" +
        "<p><b>★</b> — закладки. Горит янтарём, если текущая папка уже заложена. Внутри первой " +
        "строкой предлагается заложить её, тап по закладке переносит, долгий тап удаляет. " +
        "Помнится двадцать штук.</p>" +
        "<p>Закладка помнит не только папку, но и том, на котором та лежит. Папку с карты памяти " +
        "можно заложить наравне с внутренней: при переходе Hark сам переключит дерево. Если доступ " +
        "к тому утрачен, закладка рисуется кирпичным и с прочерком.</p>" +
        "<p><b>Логотип</b> открывает сортировку, историю, выбор папки, настройки и эту справку.</p>" +

        "<h3>Виджет</h3>" +
        "<p>Имя файла, папка, полоса позиции, время и пять кнопок: предыдущий, минус пятнадцать, " +
        "пуск, плюс тридцать, следующий. Справа speed — тап меняет её по кругу, не открывая " +
        "приложение. Тап по имени или по фону открывает Hark на текущей папке.</p>" +
        "<p><b>Обложка</b> справа. Сперва ищется картинка, вшитая в файл; если её нет — изображение, " +
        "лежащее в той же папке, причём cover, folder, front и album имеют преимущество перед случайным " +
        "снимком. Второй путь важнее первого: у книги, нарезанной на главы, тегов обычно нет вовсе, " +
        "зато рядом лежит обложка одним файлом. Нет ни того, ни другого — квадрат исчезает, остальное " +
        "занимает его место.</p>" +
        "<p>Та же обложка помельче стоит в нижней панели приложения. Тап по ней разворачивает панель.</p>" +
        "<p>Кнопки работают, даже когда приложение выгружено из памяти: плеер поднимается сам, читает " +
        "последний файл и позицию и продолжает с того же места. Проверить, что произошло, можно в " +
        "дознании.</p>" +
        "<p>Заливка и рамка — разные слои. В настройках плотность заливки крутится пятью ступенями " +
        "вплоть до нуля; рамка остаётся всегда, иначе на прозрачном фоне содержимое выглядит " +
        "рассыпанным по столу.</p>" +

        "<h3>Экран</h3>" +
        "<p>Тап по файлу — играет. Долгий тап — поделиться, играть с начала, забыть позицию, сведения.</p>" +
        "<p>Тап по нижней панели разворачивает её на весь экран. Перетаскивание ловится по всей " +
        "высоте панели, а не по двухпиксельной нитке.</p>" +
        "<p>Полоска под именем файла показывает, где вы остановились. Дослушанные гаснут. " +
        "Играющий файл получает вертикальную черту слева: плашка кричит, черта говорит.</p>" +

        "<h3>Полный экран</h3>" +
        "<p>Тап по нижней панели разворачивает её. Здесь всё крупно и под большим пальцем:</p>" +
        "<p><b>1.25×</b> слева — скорость. Пять ступеней от 0.75 до 2.0, значение общее для всего " +
        "приложения и переживает перезапуск. Янтарным горит, когда speed не единица.</p>" +
        "<p><b>15 и 30</b> — назад на пятнадцать секунд, вперёд на тридцать. Асимметрия намеренная: " +
        "переслушивают короче, чем пропускают.</p>" +
        "<p><b>сон</b> справа — таймер: пятнадцать, тридцать, шестьдесят минут или до конца файла. " +
        "За двадцать секунд до срабатывания звук плавно гаснет, а не обрывается. Пока таймер идёт, " +
        "на его месте виден остаток.</p>" +
        "<p><b>Крупные цифры</b> — текущая позиция, под ними остаток. Долгий тап по ним открывает " +
        "точный ввод: часы, минуты, секунды. Это единственный честный способ попасть в нужное место " +
        "двухчасовой записи.</p>" +
        "<p><b>Полоса</b> под цифрами: тап — переход к точке, перетаскивание — то же, но с крупной " +
        "плашкой над пальцем, показывающей, куда вы едете. Отпустили — прыжок.</p>" +
        "<p>Треугольник внизу или кнопка «назад» возвращают к списку. Скорость и таймер продублированы " +
        "и на главном экране, по краям от кнопок.</p>" +

        "<h3>Фильтр</h3>" +
        "<p>По умолчанию видно всё звуковое. Чип в углу сужает список до MP4-семейства: m4a, m4b, " +
        "mp4, aac. Состояние запоминается; в настройках память фильтра можно отключить.</p>" +
        "<p>Если фильтр спрятал все файлы, список не остаётся пустым молча: посреди экрана " +
        "появляется надпись, сколько файлов скрыто, и тап по ней снимает фильтр.</p>" +
        "<p>Файлы MP4 всегда идут первыми, каким бы ни был порядок сортировки.</p>" +
        "<p>Проверяются и расширение, и MIME: часть прошивок вместо честного типа отдаёт " +
        "octet-stream, и фильтр по одному признаку съел бы половину файлов.</p>" +

        "<h3>Наушники и звонки</h3>" +
        "<p>Когда звук уходит в гарнитуру, в шапке появляется значок: янтарный для Bluetooth, " +
        "серый для провода. Рядом заряд, если гарнитура его сообщает. В виджете то же самое, " +
        "пилюлей у правого края.</p>" +
        "<p>Заряд Android наружу не отдаёт: нужный метод скрыт, и Hark достаёт его окольным " +
        "путём, а часть гарнитур и прошивок молчит вовсе. Тогда указатель исчезает целиком — " +
        "и в шапке, и в виджете. Пустая рамка обещала бы данные, которых не будет, и занимала " +
        "бы место зря. Это не поломка, а честный предел возможного.</p>" +
        "<p>Android сам ничего не останавливает при отключении наушников — он лишь перебрасывает " +
        "звук на динамик. Hark слушает системное извещение и ставит паузу сам. Возобновление " +
        "только вручную: наушники, воткнутые обратно, не должны запускать звук.</p>" +
        "<p>Звонок или навигатор — пауза с автоматическим возвратом. Короткое уведомление — " +
        "не пауза, а приглушение.</p>" +

        "<h3>Дознание</h3>" +
        "<p>Долгий тап по чипу фильтра открывает отчёт: что за MIME отдала прошивка каждому " +
        "файлу, живо ли разрешение на папку, дали ли аудиофокус, сработало ли извещение о " +
        "наушниках. Сверху поле отбора — наберите слово, и останутся только нужные строки.</p>" +
        "<p>Журнал живёт в памяти и умирает вместе с приложением. Единственное, что переживает " +
        "смерть, — стек последнего падения: он пишется в файл, и при следующем запуске Hark " +
        "сообщает о случившемся и кладёт подробности в тот же отчёт. Кнопка «Скопировать» " +
        "отдаёт его целиком.</p>" +

        "<h3>Доступ</h3>" +
        "<p>Ни интернета, ни местоположения, ни учётных записей, ни рекламы, ни слежки. " +
        "К папке доступ выдаёте вы сами, один раз, системным выбором. Всё остальное — " +
        "уведомление о воспроизведении и пробуждение на время звучания.</p>" +

        "<h3>Источники</h3>" +
        "<p>Логика и код: <a href=\"" + CLAUDE + "\">claude.ai</a> — Claude Opus 5<br>" +
        "Исходники: <a href=\"" + SRC + "\">github.com/Shumtugle/hark</a></p>";

    private static final String EN_DOC =
        "<p><b>Hark " + VERSION + "</b></p>" +
        "<p>A player for long recordings from a single folder. Not a library: Hark opens the " +
        "folder as it lies on disk and does not know the words \"artist\" and \"album\". " +
        "Code, layout and logic fully generated by AI, with no human involvement.</p>" +

        "<h3>How it thinks</h3>" +
        "<p><b>Position matters most.</b> The place inside every file is remembered separately, " +
        "keyed by documentId rather than \"last track\". Returning rewinds five seconds: come back " +
        "to a lecture after a week and you hear the end of the previous sentence, not the middle " +
        "of a word.</p>" +
        "<p><b>A folder is read in one query.</b> No storage scan, no database, no index. Length is " +
        "fetched lazily as rows are drawn, because opening metadata for three hundred files at once " +
        "is several seconds of silence.</p>" +
        "<p><b>A two-hour file breaks the scrub bar.</b> One pixel is seven seconds, so precise " +
        "seeking by drag is physically impossible. Hence three levels of access: fifteen- and " +
        "thirty-second buttons, coarse dragging across the whole height of the panel, and exact " +
        "time entry on a long press of the digits.</p>" +

        "<h3>The header</h3>" +
        "<p>Back arrow, folder name, filter, star, logo. Nothing else lives up there: everything " +
        "needed occasionally sits behind the logo.</p>" +
        "<p><b>MP4</b> is the filter; everything audible shows by default, a tap narrows to the MP4 family, and the choice is remembered. " +
        "A long press opens the inquest, showing what MIME the firmware gave each file, whether audio " +
        "focus was granted, whether the headphone notice fired.</p>" +
        "<p><b>★</b> holds bookmarks and glows amber when the current folder is already saved. Inside, " +
        "the first row offers to bookmark it; tap a bookmark to go there, long press to remove. " +
        "Twenty are kept.</p>" +
        "<p>A bookmark remembers not just the folder but the volume it sits on. A folder on a memory card " +
        "can be bookmarked alongside internal storage: Hark switches trees on the way. If access to a " +
        "volume is lost, the bookmark is drawn in brick with a dash.</p>" +
        "<p><b>The logo</b> opens sorting, history, the folder picker, settings and this text.</p>" +

        "<h3>The widget</h3>" +
        "<p>File name, folder, position bar, times, and five buttons: previous, minus fifteen, play, plus " +
        "thirty, next. Speed sits on the right and cycles on tap without opening the app. Tapping the name " +
        "or the background opens Hark on the current folder.</p>" +
        "<p><b>The cover</b> sits on the right. Artwork embedded in the file comes first; failing that, an " +
        "image lying in the same folder, where cover, folder, front and album outrank a stray screenshot. " +
        "The second path matters more than the first: a book cut into chapters usually carries no tags at " +
        "all, yet the cover sits right there as a file. With neither, the square disappears and the rest " +
        "takes its place.</p>" +
        "<p>The same cover, smaller, sits in the app's bottom panel. Tapping it expands the panel.</p>" +
        "<p>The buttons work even when the app has been evicted from memory: the player raises itself, reads " +
        "the last file and position, and carries on from the same second. The inquest records what " +
        "happened.</p>" +
        "<p>Fill and frame are separate layers. Settings dial the fill through five steps down to nothing; " +
        "the frame always stays, because without it the contents look scattered across the wallpaper.</p>" +

        "<h3>The screen</h3>" +
        "<p>Tap a file to play. Long press for share, play from the start, forget position, details.</p>" +
        "<p>Tap the bottom panel to expand it full screen. Dragging is caught across the entire panel, " +
        "not on the two-pixel thread.</p>" +
        "<p>The stripe under a name shows where you stopped. Finished files dim. The playing file gets " +
        "a vertical rule on the left: a filled block shouts, a rule speaks.</p>" +

        "<h3>Full screen</h3>" +
        "<p>Tapping the bottom panel expands it. Everything here is large and within reach of a thumb:</p>" +
        "<p><b>1.25×</b> on the left is speed. Five steps from 0.75 to 2.0, shared across the app and " +
        "kept between launches. It glows amber whenever the speed is not one.</p>" +
        "<p><b>15 and 30</b> — back fifteen seconds, forward thirty. The asymmetry is deliberate: " +
        "people re-listen in shorter jumps than they skip.</p>" +
        "<p><b>sleep</b> on the right — fifteen, thirty, sixty minutes or until the file ends. " +
        "The volume fades over the last twenty seconds instead of cutting off. While a timer runs, " +
        "the remaining time shows in its place.</p>" +
        "<p><b>The large digits</b> are the current position, with the remainder beneath. A long press " +
        "opens exact entry: hours, minutes, seconds — the only honest way into the middle of a " +
        "two-hour recording.</p>" +
        "<p><b>The bar</b> below: tap to jump, drag to jump with a large plate above your finger showing " +
        "where you are heading. Release and it goes.</p>" +
        "<p>The triangle at the bottom, or the back button, returns to the list. Speed and the sleep " +
        "timer are mirrored on the main screen, flanking the buttons.</p>" +

        "<h3>The filter</h3>" +
        "<p>Everything audible is visible by default. The chip in the corner narrows the list to the " +
        "MP4 family: m4a, m4b, mp4, aac. The choice is remembered; the memory can be switched off " +
        "in settings.</p>" +
        "<p>When a filter hides every file, the list does not stay empty in silence: a line in the " +
        "middle says how many files are hidden, and tapping it lifts the filter.</p>" +
        "<p>MP4 files always come first, whatever the sort order.</p>" +
        "<p>Both extension and MIME are checked: some firmware returns octet-stream instead of an " +
        "honest type, and a filter on one signal alone would swallow half the folder.</p>" +

        "<h3>Headphones and calls</h3>" +
        "<p>When sound leaves for a headset, a mark appears in the header: amber for Bluetooth, " +
        "grey for a cable, with the charge beside it when the hardware will say. The widget shows " +
        "the same in a pill at its right edge.</p>" +
        "<p>Android does not expose the charge publicly: the method is hidden and Hark reaches it " +
        "sideways, while some headsets and some firmware never report it at all. The indicator " +
        "then disappears entirely, in the header and in the widget alike: an empty frame would " +
        "promise data that is not coming and take up room for nothing. Not a fault, but the " +
        "honest limit.</p>" +
        "<p>Android stops nothing when headphones are pulled — it merely moves the sound to the " +
        "speaker. Hark listens for the system notice and pauses itself. Resuming is manual only: " +
        "headphones plugged back in should not start the sound.</p>" +
        "<p>A call or navigation pauses with automatic return. A brief notification ducks the volume " +
        "instead of pausing.</p>" +

        "<h3>The inquest</h3>" +
        "<p>A long press on the filter chip opens the report: what MIME the firmware gave each " +
        "file, whether the folder permission is alive, whether audio focus was granted, whether " +
        "the headphone notice fired. A filter field at the top selects lines rather than " +
        "highlighting them.</p>" +
        "<p>The log lives in memory and dies with the app. The one thing that survives death is " +
        "the stack of the last crash: it goes to a file, and on the next launch Hark says so and " +
        "puts the details into the same report. Copy takes the whole of it.</p>" +

        "<h3>Access</h3>" +
        "<p>No internet, no location, no accounts, no ads, no tracking. You grant access to the folder " +
        "yourself, once, through the system picker. Everything else is the playback notification and " +
        "staying awake while sound is running.</p>" +

        "<h3>Sources</h3>" +
        "<p>Logic and code: <a href=\"" + CLAUDE + "\">claude.ai</a> — Claude Opus 5<br>" +
        "Source: <a href=\"" + SRC + "\">github.com/Shumtugle/hark</a></p>";
}
