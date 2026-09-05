package io.github.shumtugle.hark;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;

public final class I18n {

    public static final String[] CODES = {
        "auto","en","ru","fi","sv","de","fr","es","uk","zh","ar","iw","ur","ky","kk" };
    public static final String[] FLAGS = {
        "\uD83C\uDF10","\uD83C\uDDEC\uD83C\uDDE7","\uD83C\uDDF7\uD83C\uDDFA",
        "\uD83C\uDDEB\uD83C\uDDEE","\uD83C\uDDF8\uD83C\uDDEA","\uD83C\uDDE9\uD83C\uDDEA",
        "\uD83C\uDDEB\uD83C\uDDF7","\uD83C\uDDEA\uD83C\uDDF8","\uD83C\uDDFA\uD83C\uDDE6",
        "\uD83C\uDDE8\uD83C\uDDF3","\uD83C\uDDF8\uD83C\uDDE6","\uD83C\uDDEE\uD83C\uDDF1",
        "\uD83C\uDDF5\uD83C\uDDF0","\uD83C\uDDF0\uD83C\uDDEC","\uD83C\uDDF0\uD83C\uDDFF" };
    public static final String[] NAMES = {
        "Auto","English","Русский","Suomi","Svenska","Deutsch","Français","Español",
        "Українська","中文","العربية","עברית","اردو","Кыргызча","Қазақша" };
    private static final String[] RTL_CODES = {"ar","iw","ur"};

    public static final int
        NOTHING=0, CONT=1, OF=2, OUTSIDE=3, SETTINGS=4, ABOUT=5, LANGUAGE=6,
        SORT=7, S_NAME=8, S_PLAYED=9, S_DURATION=10, S_MODIFIED=11, SORT_HINT=12,
        PICK=13, TO_ROOT=14, HISTORY=15, HIST_EMPTY=16,
        SHARE=17, FROM_START=18, FORGET=19, DETAILS=20,
        SPEED=21, SLEEP=22, SLEEP_OFF=23, MINUTES=24, TILL_END=25,
        JUMP_TO=26, JUMP=27, CANCEL=28, CLOSE=29, COPY=30, CLEAR=31, COPIED=32,
        GONE=33, NO_PLAYER=34, SEND=35, SEND_FAIL=36, LEFT=37, PROBE=38,
        NO_FOLDER=39, H=40, M=41, S=42,
        F_NAME=43, F_MIME=44, F_SIZE=45, F_DUR=46, F_POS=47, F_ID=48,
        NOT_READ=49, UNKNOWN=50, NOT_SENT=51, TILL_END_SHORT=52, PLAYBACK=53,
        MB=54, ALL=55, NOTHING_YET=56, REMEMBER_FILTER=57, WIDGET=58, WIDGET_FRAME=59,
        MARKS=60, MARK_ADD=61, MARK_NONE=62, MARK_DEL=63, ROOT_SHORT=64,
        HIDDEN_1=65, HIDDEN_2=66, HIDDEN_3=67, EMPTY_FOLDER=68, CRASHED=69, RESUME_BACK=70;
    public static final int COUNT = 71;

    static final String[] EN = {
        "Nothing selected","Continue","of","Outside","Settings","About","Language",
        "Sort","by name","by last played","by length","by file date",
        "Choosing the same again reverses the order",
        "Choose another folder","To the root","History","History is empty",
        "Share","Play from the start","Forget position","Details",
        "Speed","Sleep","Cancel timer","minutes","Until the file ends",
        "Go to time","Go","Cancel","Close","Copy","Clear","Copied",
        "The file is gone","Player is not running","Send","Could not send","left","Inquest",
        "Folder unavailable","h","min","s",
        "Name","MIME from the system","Size","Length","Position","documentId",
        "not read","unknown","not reported","till end","Playback",
        "MB","ALL","Nothing has happened yet.",
        "Remember the filter",
        "Widget backdrop",
        "Widget frame",
        "Bookmarks", "Bookmark this folder", "No bookmarks yet", "Long press to remove", "root",
        "The filter hides", "files here", "Tap to show everything", "This folder is empty", "Hark closed unexpectedly last time. The inquest kept the details.",
        "Rewind on resume" };

    static final String[] RU = {
        "Ничего не выбрано","Продолжить","из","Извне","Настройки","О программе","Язык",
        "Сортировка","по имени","по прослушиванию","по длительности","по дате файла",
        "Повторный выбор переворачивает порядок",
        "Выбрать другую папку","В корень","История","История пуста",
        "Поделиться","Играть с начала","Забыть позицию","Сведения",
        "Скорость","Сон","Отменить таймер","минут","До конца файла",
        "Перейти к времени","Перейти","Отмена","Закрыть","Скопировать","Очистить","Скопировано",
        "Файл исчез","Плеер не запущен","Отправить","Не удалось отправить","осталось","Дознание",
        "Папка недоступна","ч","мин","сек",
        "Имя","MIME от системы","Размер","Длительность","Позиция","documentId",
        "не прочитана","неизвестен","не сообщён","до конца","Воспроизведение",
        "МБ","ВСЁ","Пока ничего не произошло.",
        "Помнить фильтр",
        "Подложка виджета",
        "Рамка виджета",
        "Закладки", "Заложить эту папку", "Закладок пока нет", "Долгий тап удаляет", "корень",
        "Фильтр прячет", "файлов", "Нажмите, чтобы показать всё", "В этой папке пусто", "В прошлый раз Hark закрылся сам. Подробности сохранены в дознании.",
        "Откат при продолжении" };

    static final String[] FI = {
        "Ei valintaa","Jatka","/","Ulkoa","Asetukset","Tietoja","Kieli",
        "Järjestys","nimen mukaan","kuuntelun mukaan","keston mukaan","päivämäärän mukaan",
        "Sama valinta uudelleen kääntää järjestyksen",
        "Valitse toinen kansio","Juureen","Historia","Historia on tyhjä",
        "Jaa","Toista alusta","Unohda kohta","Tiedot",
        "Nopeus","Uni","Peruuta ajastin","minuuttia","Tiedoston loppuun",
        "Siirry aikaan","Siirry","Peruuta","Sulje","Kopioi","Tyhjennä","Kopioitu",
        "Tiedosto on poissa","Soitin ei ole käynnissä","Lähetä","Lähetys epäonnistui","jäljellä","Tutkinta",
        "Kansio ei ole käytettävissä","t","min","s",
        "Nimi","MIME järjestelmältä","Koko","Kesto","Kohta","documentId",
        "ei luettu","tuntematon","ei ilmoitettu","loppuun","Toisto",
        "Mt","KAIKKI","Mitään ei ole vielä tapahtunut.",
        "Muista suodatin",
        "Widgetin tausta",
        "Widgetin kehys",
        "Kirjanmerkit", "Merkitse tämä kansio", "Ei kirjanmerkkejä", "Pitkä painallus poistaa", "juuri",
        "Suodatin piilottaa", "tiedostoa", "Napauta näyttääksesi kaiken", "Tämä kansio on tyhjä", "Hark sulkeutui viimeksi itsestään. Tiedot ovat tutkinnassa.",
        "Kelaus jatkettaessa" };

    static final String[] SV = {
        "Inget valt","Fortsätt","av","Utifrån","Inställningar","Om","Språk",
        "Sortering","efter namn","efter senast lyssnat","efter längd","efter fildatum",
        "Samma val igen vänder ordningen",
        "Välj en annan mapp","Till roten","Historik","Historiken är tom",
        "Dela","Spela från början","Glöm positionen","Detaljer",
        "Hastighet","Sömn","Avbryt timern","minuter","Tills filen tar slut",
        "Gå till tid","Gå","Avbryt","Stäng","Kopiera","Rensa","Kopierat",
        "Filen är borta","Spelaren körs inte","Skicka","Kunde inte skicka","kvar","Utredning",
        "Mappen är otillgänglig","tim","min","s",
        "Namn","MIME från systemet","Storlek","Längd","Position","documentId",
        "inte läst","okänd","inte angiven","till slutet","Uppspelning",
        "MB","ALLT","Inget har hänt än.",
        "Kom ihåg filtret",
        "Widgetens bakgrund",
        "Widgetens ram",
        "Bokmärken", "Bokmärk denna mapp", "Inga bokmärken än", "Långt tryck tar bort", "rot",
        "Filtret döljer", "filer", "Tryck för att visa allt", "Mappen är tom", "Hark stängdes oväntat förra gången. Detaljerna finns i utredningen.",
        "Backa vid återupptagning" };

    static final String[] DE = {
        "Nichts ausgewählt","Fortsetzen","von","Von außen","Einstellungen","Über","Sprache",
        "Sortierung","nach Name","nach zuletzt gehört","nach Länge","nach Dateidatum",
        "Dieselbe Wahl erneut kehrt die Reihenfolge um",
        "Anderen Ordner wählen","Zum Stammordner","Verlauf","Der Verlauf ist leer",
        "Teilen","Von vorn abspielen","Position vergessen","Details",
        "Tempo","Schlaf","Timer abbrechen","Minuten","Bis zum Ende der Datei",
        "Zur Zeit springen","Springen","Abbrechen","Schließen","Kopieren","Leeren","Kopiert",
        "Die Datei ist verschwunden","Der Player läuft nicht","Senden","Senden fehlgeschlagen","übrig","Befund",
        "Ordner nicht verfügbar","Std","Min","Sek",
        "Name","MIME vom System","Größe","Länge","Position","documentId",
        "nicht gelesen","unbekannt","nicht gemeldet","bis zum Ende","Wiedergabe",
        "MB","ALLE","Bisher ist nichts passiert.",
        "Filter merken",
        "Widget-Hintergrund",
        "Widget-Rahmen",
        "Lesezeichen", "Diesen Ordner merken", "Noch keine Lesezeichen", "Langes Tippen entfernt", "Stamm",
        "Der Filter verbirgt", "Dateien", "Tippen, um alles zu zeigen", "Dieser Ordner ist leer", "Hark hat sich zuletzt unerwartet beendet. Einzelheiten im Befund.",
        "Rücklauf beim Fortsetzen" };

    static final String[] FR = {
        "Rien de sélectionné","Reprendre","sur","De l'extérieur","Réglages","À propos","Langue",
        "Tri","par nom","par dernière écoute","par durée","par date du fichier",
        "Le même choix à nouveau inverse l'ordre",
        "Choisir un autre dossier","Vers la racine","Historique","L'historique est vide",
        "Partager","Lire depuis le début","Oublier la position","Détails",
        "Vitesse","Sommeil","Annuler la minuterie","minutes","Jusqu'à la fin du fichier",
        "Aller au temps","Aller","Annuler","Fermer","Copier","Effacer","Copié",
        "Le fichier a disparu","Le lecteur n'est pas lancé","Envoyer","Échec de l'envoi","restant","Enquête",
        "Dossier indisponible","h","min","s",
        "Nom","MIME du système","Taille","Durée","Position","documentId",
        "non lue","inconnue","non communiqué","jusqu'à la fin","Lecture",
        "Mo","TOUT","Rien ne s'est encore produit.",
        "Mémoriser le filtre",
        "Fond du widget",
        "Cadre du widget",
        "Signets", "Ajouter ce dossier", "Aucun signet", "Appui long pour retirer", "racine",
        "Le filtre masque", "fichiers", "Appuyez pour tout afficher", "Ce dossier est vide", "Hark s\u2019est fermé seul la dernière fois. Détails dans l\u2019enquête.",
        "Retour à la reprise" };

    static final String[] ES = {
        "Nada seleccionado","Continuar","de","Desde fuera","Ajustes","Acerca de","Idioma",
        "Orden","por nombre","por última escucha","por duración","por fecha del archivo",
        "Elegir lo mismo otra vez invierte el orden",
        "Elegir otra carpeta","A la raíz","Historial","El historial está vacío",
        "Compartir","Reproducir desde el principio","Olvidar la posición","Detalles",
        "Velocidad","Sueño","Cancelar el temporizador","minutos","Hasta el final del archivo",
        "Ir al tiempo","Ir","Cancelar","Cerrar","Copiar","Limpiar","Copiado",
        "El archivo ha desaparecido","El reproductor no está en marcha","Enviar","No se pudo enviar","restante","Indagación",
        "Carpeta no disponible","h","min","s",
        "Nombre","MIME del sistema","Tamaño","Duración","Posición","documentId",
        "no leída","desconocido","no informado","hasta el final","Reproducción",
        "MB","TODO","Todavía no ha pasado nada.",
        "Recordar el filtro",
        "Fondo del widget",
        "Marco del widget",
        "Marcadores", "Marcar esta carpeta", "Sin marcadores", "Pulsación larga para quitar", "raíz",
        "El filtro oculta", "archivos", "Toque para mostrar todo", "Esta carpeta está vacía", "Hark se cerró solo la última vez. Los detalles están en la indagación.",
        "Retroceso al reanudar" };

    private static final HashMap<String, String[]> T = new HashMap<>();
    private static String active = "en";

    static {
        T.put("en", EN); T.put("ru", RU); T.put("fi", FI); T.put("sv", SV);
        T.put("de", DE); T.put("fr", FR); T.put("es", ES);
        Lang.register(T);
    }

    private I18n() {}

    public static void apply(Context c, String pref) {
        String code = pref;
        if (code == null || code.equals("auto")) {
            Locale l = c.getResources().getConfiguration().getLocales().get(0);
            code = l.getLanguage();
            if ("he".equals(code)) code = "iw";
            if ("in".equals(code)) code = "id";
        }
        active = T.containsKey(code) ? code : "en";
    }

    public static String code() { return active; }

    public static boolean rtl() {
        for (String r : RTL_CODES) if (r.equals(active)) return true;
        return false;
    }

    public static String t(int key) {
        String[] a = T.get(active);
        if (a == null || key >= a.length || a[key] == null) a = EN;
        return (key < a.length && a[key] != null) ? a[key] : EN[key];
    }

    public static String nameOf(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
        return code;
    }
}
