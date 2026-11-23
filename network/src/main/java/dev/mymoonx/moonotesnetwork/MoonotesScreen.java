package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Moonotes manager. All coordinates use the game's scaled GUI space. */
public final class MoonotesScreen extends Screen {
    private static final int TEXT = MoonotesTheme.TEXT, MUTED = MoonotesTheme.MUTED, BLUE = MoonotesTheme.ACCENT;
    private static final int GREEN = 0xFFA3DCC0, WARNING = 0xFFFFC399;
    private static final String[] TABS = {"Библиотека", "Каталоги", "Загрузка", "Настройки"};
    private static final String[] SORT_LABELS = {"Имя А–Я", "Размер ↓", "Загружены ↓", "Использованы ↓"};
    private static final String[] FILTER_LABELS = {"Все эмоты", "В кеше", "Не загружены", "Избранное", "Недавно"};
    private static final long TOOLTIP_DELAY = 500_000_000L;

    private int tab, sortMode, filterMode, keyboardPosition = -1;
    private final Screen parent;
    private int settingsSection;
    private static final String[] SETTING_SECTIONS={"Чат", "Сеть", "Вид", "Система", "Эмоты+"};
    private static final int[][] SETTING_OPTIONS={{8,9,3,4},{0,1,2,6},{10,14,11,12,13},{7,5},{15,16,17,18,19,20}};
    private final List<net.minecraft.util.FormattedCharSequence> wrappedReportLines=new ArrayList<>();
    private int reportWrapWidth=-1;
    private String wrappedReport="";
    private String filter = "", selectedId = "", selectedName = "", report = "";
    private long selectedBytes;
    private boolean selectedCached, reportOpen, releaseNotesOpen, eventsOpen;
    private boolean catalogFormOpen, catalogPickerOpen, cacheOpen, removeConfirm, catalogPreviewOpen, catalogErrorReport;
    private long observedPreviewToken=-1;
    private String catalogDetailKey="", importTarget="", formValue="";
    private int formMode, catalogRows, catalogTop;
    private long observedRevision=-1, importGeneration;
    private boolean fileImportBusy;
    private final ScrollState catalogScroll=new ScrollState(), pickerScroll=new ScrollState();
    private MoonotesLayout.Rect catalogTrack;
    private EditBox catalogField;
    private EditBox quickChannelField;
    private Button quickAddButton,catalogDetailsButton,previewConfirmButton;
    private String quickChannel = "";
    private MultiLineEditBox jsonField;
    private Button submitCatalogButton, healthButton, repairButton;
    private final LibraryPreferences preferences=LibraryPreferences.local();
    private MoonotesLayout layout;
    private final ScrollState libraryScroll = new ScrollState();
    private final ScrollState settingsScroll = new ScrollState();
    private final ScrollState reportScroll = new ScrollState();
    private EmotePreviews previews = new EmotePreviews();
    private final List<Integer> filtered = new ArrayList<>();
    private final List<String> reportLines = new ArrayList<>();
    private EditBox searchField;
    private Button startButton, pauseButton, stopButton, refreshButton, copyButton, reportButton;
    private boolean filterDirty = true;
    private long filterUpdated, copiedUntil, hoverSince;
    private String hoveredTarget = "", statusNotice = "";
    private long noticeUntil;
    private int observedMouseClicks, observedMouseDrags;
    private int settingColumns, settingRows, settingsTop;
    private MoonotesLayout.Rect settingsTrack;

    public MoonotesScreen() { this(null); }
    public MoonotesScreen(Screen parent) { super(Component.literal("Moonotes")); this.parent=parent; }
    @Override public void onClose() { MoonotesNetworkClient.cancelCatalogPreview();if(minecraft!=null)minecraft.gui.setScreen(parent); }
    public boolean isDownloadPageVisible() { return tab==2 && !overlayOpen(); }

    @Override protected void init() {
        super.init();
        if(previews==null)previews=new EmotePreviews();
        libraryScroll.release();
        settingsScroll.release();
        reportScroll.release();
        layout = new MoonotesLayout(width, height);
        filterDirty = true;
        rebuildMoonotesWidgets();
        if (MoonotesNetworkClient.releaseNotesPending()) openReleaseNotes();
    }

    private boolean overlayOpen() { return reportOpen || !selectedId.isEmpty() || catalogFormOpen || catalogPickerOpen || cacheOpen || catalogPreviewOpen || !catalogDetailKey.isEmpty(); }

    private MoonotesButton button(MoonotesLayout.Rect bounds, String label, String tooltip,
                                  boolean accent, Button.OnPress action) {
        return addRenderableWidget(new MoonotesButton(bounds, label, tooltip, accent, action));
    }
    private MoonotesLayout.Rect rect(int x, int y, int w, int h) {
        return new MoonotesLayout.Rect(x, y, Math.max(1,w), Math.max(1,h));
    }

    private void rebuildMoonotesWidgets() {
        if (minecraft == null) return;
        if (layout == null) layout = new MoonotesLayout(width,height);
        boolean searchFocused = searchField != null && searchField.isFocused();
        boolean quickFocused = quickChannelField != null && quickChannelField.isFocused();
        clearFocus();
        setDragging(false);
        clearWidgets();
        searchField = null;
        startButton = pauseButton = stopButton = refreshButton = copyButton = reportButton = null;
        submitCatalogButton=healthButton=repairButton=null;
        catalogField=null;jsonField=null;quickChannelField=null;quickAddButton=null;catalogDetailsButton=null;previewConfirmButton=null;
        hoveredTarget = "";
        if (overlayOpen()) {
            buildModalButtons();
            return;
        }
        var p = layout.panel;
        button(layout.closeButton(),"×",
            "Закрыть Moonotes. Начатая загрузка продолжится в фоне.",false,b->onClose());
        int healthX=layout.contentX+83;
        healthButton=button(rect(healthX,p.y()+6,layout.wide?106:58,20),"Проверка…",
            "Состояние регистрации названий для чата и кеша изображений. Нажмите для проверки и восстановления.",false,b->{cacheOpen=true;rebuildMoonotesWidgets();});
        int tabW = (layout.contentWidth - 18) / 4;
        for (int i=0;i<TABS.length;i++) {
            final int index=i;
            button(rect(layout.contentX+i*(tabW+6),p.y()+34,tabW,22),TABS[i],
                switch (i) {
                    case 0 -> "Поиск, избранное, история и просмотр подключённых каталогов.";
                    case 1 -> "Добавление канала, набора 7TV или JSON. Управление каталогами и каналами для этого мира.";
                    case 2 -> "Загрузка недостающих смайликов в общий кеш Streamotes.";
                    default -> "Источники загрузки, уведомления и локальная диагностика.";
                },tab==i,b->switchTab(index));
        }
        switch (tab) {
            case 0 -> buildLibraryWidgets(searchFocused);
            case 1 -> buildCatalogWidgets();
            case 2 -> buildDownloadWidgets();
            default -> buildSettingsWidgets();
        }
        if(tab==1 && quickFocused && quickChannelField!=null)setFocused(quickChannelField);
    }

    private void switchTab(int index) {
        if (index==tab) return;
        libraryScroll.release();
        settingsScroll.release();
        clearFocus();
        tab=index;
        filterDirty=true;
        rebuildMoonotesWidgets();
    }

    private void buildLibraryWidgets(boolean restoreFocus) {
        int x=layout.contentX,y=layout.contentTop+2,w=layout.contentWidth;
        int controlWidth=layout.wide?118:Math.max(46,w/6);
        int catalogWidth=layout.wide?Math.min(178,w/4):Math.max(70,w/4);
        int searchWidth=Math.max(45,w-catalogWidth-controlWidth*2-18);
        button(rect(x,y,catalogWidth,22),MoonotesNetworkClient.selectedCatalogLabel()+" ▾",
            "Выбрать все каталоги или один из подключённых.",false,b->{catalogPickerOpen=true;pickerScroll.row=0;rebuildMoonotesWidgets();});
        int searchX=x+catalogWidth+6;
        searchField=addRenderableWidget(new MoonotesEditBox(font,searchX,y,searchWidth,22,Component.literal("Поиск смайлика")));
        searchField.setHint(Component.literal("Поиск по названию…"));
        searchField.setMaxLength(80);
        searchField.setValue(filter);
        searchField.setTooltip(Tooltip.create(Component.literal("Фильтр по части названия. Поиск работает по всему набору.")));
        searchField.setTooltipDelay(Duration.ofMillis(500));
        searchField.setResponder(value->{
            filter=value;filterDirty=true;libraryScroll.row=0;keyboardPosition=-1;
        });
        if (restoreFocus) setFocused(searchField);
        int buttonsY=y;
        int sortX=searchX+searchWidth+6;
        int sortW=controlWidth;
        button(rect(sortX,buttonsY,sortW,22), (layout.wide?"Сорт.: ":"")+SORT_LABELS[sortMode],
            "Порядок: имя А–Я → большие файлы → недавно загруженные → недавно использованные.\nНажмите, чтобы выбрать следующий вариант.",
            false,b->{sortMode=(sortMode+1)%SORT_LABELS.length;filterDirty=true;libraryScroll.row=0;keyboardPosition=-1;rebuildMoonotesWidgets();});
        button(rect(sortX+sortW+6,buttonsY,sortW,22),FILTER_LABELS[filterMode],
            "Показывать: все → в кеше → отсутствующие → избранное → недавно использованные.\n«Недавно» пополняется после вставки в чат или копирования названия. Содержимое чата не сохраняется.",
            false,b->{filterMode=(filterMode+1)%FILTER_LABELS.length;filterDirty=true;libraryScroll.row=0;keyboardPosition=-1;rebuildMoonotesWidgets();});
        refreshButton=button(layout.footer(0,2),"Обновить список",
            "Проверить выбранные каталоги. Изображения скачиваются отдельно; существующий кеш сохраняется.",
            false,b->refreshManifest());
        startButton=button(layout.footer(1,2),"Загрузить недостающие",
            "Открыть вкладку загрузки. Само скачивание запускается её кнопкой «Скачать».",
            true,b->switchTab(2));
    }

    private void buildDownloadWidgets() {
        button(layout.downloadMinimize(),"Свернуть",
            "Закрыть окно, оставив загрузку работать. Прогресс появится в HUD, если он включён.",false,b->onClose());
        startButton=button(layout.footer(0,4),"Добрать отсутствующие",
            "Скачать только те изображения, которых нет в кеше. Существующие файлы не перезаписываются.",
            true,b->MoonotesNetworkClient.downloadFromGui());
        pauseButton=button(layout.footer(1,4),"Проверить источники",
            "Без загрузки в кеш проверить именно отсутствующий эмот через 7TV и Cloudflare. Во время загрузки — пауза/продолжение.",
            false,b->{if(MoonotesNetworkClient.isDownloading())MoonotesNetworkClient.togglePause();else CacheMaintenance.diagnoseMissing();});
        stopButton=button(layout.footer(2,4),"Кеш и проверка",
            "Открыть проверку кеша. Во время загрузки — остановить скачивание без удаления готовых файлов.",
            false,b->{if(MoonotesNetworkClient.isDownloading())MoonotesNetworkClient.cancelDownload();else {cacheOpen=true;rebuildMoonotesWidgets();}});
        refreshButton=button(layout.footer(3,4),"Обновить",
            "Проверить изменения набора на сервере. Обновление каталога не запускает скачивание.",
            false,b->refreshManifest());
    }

    private void configureSettings() {
        var grid=layout.settingsGrid(SETTING_OPTIONS[settingsSection].length);
        settingColumns=grid.columns();settingRows=grid.rows();settingsTop=grid.viewport().y();
        settingsTrack=grid.scrollbar();
        settingsScroll.configure(settingsTop,settingsTrack.height(),settingRows,grid.maxScroll(SETTING_OPTIONS[settingsSection].length));
    }

    private void buildSettingsWidgets() {
        configureSettings();
        for(int i=0;i<SETTING_SECTIONS.length;i++){
            final int section=i;
            addRenderableWidget(new MoonotesSectionButton(layout.settingsTab(i),SETTING_SECTIONS[i],switch(i){
                case 0 -> "Подбор эмотов, мини-библиотека и сообщения о загрузке.";
                case 1 -> "Источники, параллельные запросы и обновление каталогов.";
                case 2 -> "Уведомления и расположение фоновой загрузки на экране.";
                case 3 -> "Профили серверов и локальная диагностика.";
                default -> "Колесо реакций и эмоты в игровых текстах (только на этом клиенте).";
            },settingsSection==i,b->{settingsSection=section;settingsScroll.row=0;settingsScroll.release();rebuildMoonotesWidgets();}));
        }
        var grid=layout.settingsGrid(SETTING_OPTIONS[settingsSection].length);
        int[] options=SETTING_OPTIONS[settingsSection];
        for(int row=0;row<settingRows;row++)for(int column=0;column<settingColumns;column++){
            int index=(settingsScroll.row+row)*settingColumns+column;
            if(index>=options.length)continue;
            int option=options[index];var card=grid.card(column,row);
            button(rect(card.x()+7,card.y()+18,card.width()-14,20),settingValue(option),settingHint(option),false,b->{
                switch(option) {
                    case 0 -> MoonotesNetworkClient.cycleImageSource();
                    case 1 -> MoonotesNetworkClient.cycleParallelDownloads();
                    case 2 -> MoonotesNetworkClient.toggleRetryDownloads();
                    case 3 -> MoonotesNetworkClient.toggleJoinOffer();
                    case 4 -> MoonotesNetworkClient.toggleCompletionNotice();
                    case 6 -> MoonotesNetworkClient.toggleAutoCatalogSync();
                    case 7 -> MoonotesNetworkClient.toggleServerProfiles();
                    case 8 -> MoonotesNetworkClient.toggleChatSuggestions();
                    case 9 -> MoonotesNetworkClient.toggleChatPicker();
                    case 10 -> MoonotesNetworkClient.cycleNotificationMode();
                    case 11 -> MoonotesNetworkClient.toggleHudProgress();
                    case 12 -> MoonotesNetworkClient.cycleHudCorner();
                    case 13 -> { minecraft.gui.setScreen(new HudLayoutScreen(this));return; }
                    case 14 -> MoonotesNetworkClient.toggleUnifiedStreamotesNotifications();
                    case 15,16,17,18,19,20 -> ExtrasSettings.local().cycle(option);
                    default -> {MoonotesNetworkClient.toggleLocalDiagnostics();
                        if(!MoonotesNetworkClient.localDiagnostics()){observedMouseClicks=0;observedMouseDrags=0;}}
                }
                rebuildMoonotesWidgets();
            });
        }
        reportButton=button(layout.footer(0,4),"Отчёт","Локальная диагностика: версии, счётчики и категории ошибок. Отправки данных нет.",false,b->openReport());
        button(layout.footer(1,4),"Кеш","Проверка изображений и анимации, восстановление отсутствующих файлов.",false,b->{cacheOpen=true;rebuildMoonotesWidgets();});
        button(layout.footer(2,4),"Что нового","Изменения версии 0.5.3. Доступны без интернета.",false,b->openReleaseNotes());
        button(layout.footer(3,4),"События","Недавние сообщения Moonotes, в том числе при выключенных уведомлениях.",false,b->openEventLog());
    }

    private String settingTitle(int option) {
        return switch(option) {
            case 0 -> "Источник изображений";
            case 1 -> "Параллельные загрузки";
            case 2 -> "Повторы при сбоях";
            case 3 -> "Сообщение при входе";
            case 4 -> "Сообщение о завершении";
            case 5 -> "Локальная диагностика";
            case 6 -> "Обновление каталогов";
            case 7 -> "Профили серверов";
            case 8 -> "Подсказки в чате";
            case 9 -> "Мини-библиотека";
            case 10 -> "Уведомления";
            case 11 -> "Фоновая загрузка на экране";
            case 12 -> "Угол экрана";
            case 14 -> "Уведомления Streamotes";
            case 15 -> "Колесо быстрых эмотов";
            case 16 -> "Реакции над персонажем";
            case 17 -> "Длительность реакции";
            case 18 -> "Эмоты на табличках";
            case 19 -> "Эмоты в книгах";
            case 20 -> "Эмоты в названиях предметов";
            default -> "Расположение HUD";
        };
    }
    private String settingValue(int option) {
        return switch(option) {
            case 0 -> MoonotesNetworkClient.imageSource().equals("Авто")?"Авто · 7TV → Cloudflare":MoonotesNetworkClient.imageSource();
            case 1 -> MoonotesNetworkClient.parallelDownloadsLabel();
            case 2 -> onOff(MoonotesNetworkClient.retryDownloads());
            case 3 -> onOff(MoonotesNetworkClient.offerAtJoin());
            case 4 -> onOff(MoonotesNetworkClient.showCompletionInChat());
            case 5 -> onOff(MoonotesNetworkClient.localDiagnostics());
            case 6 -> onOff(MoonotesNetworkClient.autoCatalogSync());
            case 7 -> onOff(MoonotesNetworkClient.serverProfilesEnabled());
            case 8 -> onOff(MoonotesNetworkClient.chatSuggestionsEnabled());
            case 9 -> onOff(MoonotesNetworkClient.chatPickerEnabled());
            case 10 -> MoonotesNetworkClient.notificationModeLabel();
            case 11 -> onOff(MoonotesNetworkClient.hudProgressEnabled());
            case 12 -> MoonotesNetworkClient.hudCornerLabel();
            case 14 -> MoonotesNetworkClient.unifiedStreamotesNotifications()?"В общей очереди":"Отдельно";
            case 15,16,17,18,19,20 -> ExtrasSettings.local().value(option);
            default -> "Настроить расположение…";
        };
    }
    private static String onOff(boolean value) { return value?"Включено":"Выключено"; }
    private String settingHint(int option) {
        return switch(option) {
            case 0 -> "Авто: сначала 7TV, при ошибке — Cloudflare.\nЕсли 7TV недоступен, повторная проверка откладывается на минуту.\nРучной источник ограничивает загрузку выбранным сервисом.\nДля массовой загрузки изменение действует со следующего запуска.";
            case 1 -> "Авто использует 4 параллельных запроса. Можно выбрать 1, 2, 4 или 6.\nНа нестабильной сети попробуйте 1–2. Больше запросов не гарантирует большую скорость.\nИзменение применяется к следующему запуску загрузки.";
            case 2 -> "Ограниченные повторы временных сетевых ошибок, HTTP 429 и 5xx.\nОтсутствующий файл 404 не скачивается повторно с того же источника.\nДля массовой загрузки изменение действует со следующего запуска.";
            case 3 -> "После входа показать предложение скачать недостающие эмоты.\nСпособ показа выбирается в разделе «Вид → Уведомления». Окно не открывается само.";
            case 4 -> "Сообщить о результате загрузки выбранным способом уведомлений.\nИгра не ставится на паузу.";
            case 5 -> "Локальные счётчики и безопасные категории ошибок для поиска неполадок.\nВ отчёт не включаются ник, IP, чат, адрес сервера и пути к файлам.\nНичего автоматически не отправляется. Выключение очищает счётчики.";
            case 6 -> "Раз в сутки проверять изменения каталога. Новые эмоты только в списке: изображения не скачиваются без согласия.";
            case 7 -> "При входе на сервер восстановить выбранный каталог библиотеки. Адрес сервера сохраняется в локальном профиле только как хеш.";
            case 8 -> "Компактная лента подходящих эмотов. Tab и стрелки выбирают, пробел вставляет выбранный эмот. Пока выбор не активирован, пробел работает как обычно. Esc скрывает подсказки. /Команды не меняются.";
            case 9 -> "Кнопка «Эмоты» рядом со строкой чата открывает избранное, недавние, группы и поиск. Звезда меняет избранное; меню эмота управляет группами. Вставка добавляет текст в чат без отправки.";
            case 10 -> "Всплывающие, только ошибки, сообщения в чате или без уведомлений. Последние события остаются в журнале. Для основного Moonotes общий режим поддерживается с версии 0.4.1.";
            case 11 -> "Во время фоновой загрузки показывать небольшую шкалу в HUD. Скрывается автоматически и клавишей F1.";
            case 12 -> "Переставить HUD в один из четырёх углов. Точное положение можно настроить перетаскиванием.";
            case 14 -> "Автоматические сообщения Streamotes показываются по очереди с Moonotes, без наложения. Способ показа задаёт настройка «Уведомления». Если сообщения отключены в самом Streamotes, они останутся отключены. «Отдельно» возвращает обычные уведомления Streamotes.";
            case 15 -> "Нажмите G в игре (клавишу можно переназначить). ЛКМ — сразу отправить эмот в чат, Shift+ЛКМ — вставить в черновик, ПКМ — локальная реакция.";
            case 16 -> "Картинка эмота над персонажем; в первом лице — возле прицела. Без отправки реакции на сервер. Для ближайших игроков доступен локальный эффект из обычного чата.";
            case 17 -> "Время показа реакции: 2, 4 или 6 секунд.";
            case 18 -> "Показывать знакомые Streamotes-эмоты при локальном рисовании табличек. Исходный текст не изменяется.";
            case 19 -> "Оформление готовых книг и кафедр. Экран редактирования книги пока показывает обычный текст, чтобы не ломать курсор. Только на этом клиенте.";
            case 20 -> "Показывать известные эмоты в названиях предметов. Никакие данные предметов не изменяются.";
            default -> "Открыть предпросмотр и перетащить HUD в удобное место. Положение сохраняется после нажатия «Сохранить».";
        };
    }

    private void refreshManifest() {
        MoonotesNetworkClient.refreshManifest();
        filterDirty=true;
        statusNotice="Проверяем список…";
        noticeUntil=System.currentTimeMillis()+3000;
    }

    private void updateFilter() {
        long now=System.currentTimeMillis();
        if(!filterDirty && now-filterUpdated<2000)return;
        filterDirty=false;filterUpdated=now;filtered.clear();
        String needle=filter.strip().toLowerCase(Locale.ROOT);
        int count=Math.max(0,MoonotesNetworkClient.collectionCount());
        for(int i=0;i<count;i++) {
            boolean cached=MoonotesNetworkClient.itemCached(i);
            if(filterMode==1 && !cached || filterMode==2 && cached)continue;
            if(filterMode==3 && !preferences.favorite(MoonotesNetworkClient.itemKey(i)))continue;
            if(filterMode==4 && preferences.lastUsed(MoonotesNetworkClient.itemKey(i))==0)continue;
            if(!needle.isEmpty() && !MoonotesNetworkClient.itemName(i).toLowerCase(Locale.ROOT).contains(needle))continue;
            filtered.add(i);
        }
        Comparator<Integer> byName=Comparator.comparing(MoonotesNetworkClient::itemName,String.CASE_INSENSITIVE_ORDER);
        if(filterMode==4 || sortMode==3)filtered.sort(Comparator.<Integer>comparingLong(i->preferences.lastUsed(MoonotesNetworkClient.itemKey(i))).reversed().thenComparing(byName));
        else if(sortMode==1)filtered.sort(Comparator.<Integer>comparingLong(MoonotesNetworkClient::itemBytes).reversed().thenComparing(byName));
        else if(sortMode==2)filtered.sort(Comparator.<Integer>comparingLong(MoonotesNetworkClient::itemModified).reversed().thenComparing(byName));
        else filtered.sort(byName);
        keyboardPosition=-1;
    }

    private MoonotesLayout.Grid configureGrid() {
        updateFilter();
        var grid=layout.libraryGrid();
        var track=grid.scrollbar();
        libraryScroll.configure(track.y(),track.height(),grid.rows(),grid.maxScroll(filtered.size()));
        return grid;
    }

    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color) {
        g.text(font,Component.literal(value),x,y,color,false);
    }
    private void fit(GuiGraphicsExtractor g,String value,int x,int y,int maxWidth,int color) {
        if(maxWidth<=0)return;
        String output=font.width(value)<=maxWidth?value:font.plainSubstrByWidth(value,Math.max(0,maxWidth-7))+"…";
        text(g,output,x,y,color);
    }
    private void centered(GuiGraphicsExtractor g,String value,int center,int y,int maxWidth,int color) {
        String output=font.width(value)<=maxWidth?value:font.plainSubstrByWidth(value,Math.max(0,maxWidth-7))+"…";
        text(g,output,center-font.width(output)/2,y,color);
    }
    private static String size(long bytes) {
        if(bytes<1024)return bytes+" Б";
        if(bytes<1024*1024)return String.format(Locale.ROOT,"%.1f КиБ",bytes/1024d);
        if(bytes<1024L*1024*1024)return String.format(Locale.ROOT,"%.2f МиБ",bytes/(1024d*1024));
        return String.format(Locale.ROOT,"%.2f ГиБ",bytes/(1024d*1024*1024));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if(layout==null)layout=new MoonotesLayout(width,height);
        var p=layout.panel;
        g.fill(p.x()-2,p.y()+3,p.right()+2,p.bottom()+4,0x75000000);
        g.fill(p.x(),p.y(),p.right(),p.bottom(),MoonotesTheme.LINE);
        g.fill(p.x()+1,p.y()+1,p.right()-1,p.bottom()-1,MoonotesTheme.BG);
        g.fillGradient(p.x()+1,p.y()+1,p.right()-1,p.y()+29,0xFF203149,MoonotesTheme.PANEL);
        g.fill(p.x()+1,p.y()+29,p.right()-1,p.y()+30,MoonotesTheme.LINE);
        g.fill(p.x()+1,layout.footerY-5,p.right()-1,p.bottom()-1,0xFF111B2B);
        text(g,"MOONOTES",layout.contentX,p.y()+11,TEXT);
        String author=p.width()>=440?"mymoon_x · 0.6.0 α2":"";
        int authorX=layout.authorRight()-font.width(author);
        int cacheX=layout.contentX+83+(layout.wide?106:58)+8;
        int cacheWidth=Math.max(0,authorX-cacheX-8);
        String counts=Math.max(0,MoonotesNetworkClient.cachedImageCount())+"/"+Math.max(0,MoonotesNetworkClient.collectionImageCount());
        String cacheLabel=layout.wide?"Кеш "+counts+" · "+size(MoonotesNetworkClient.channelCacheSize()):"Кеш "+counts;
        if(font.width(cacheLabel)>cacheWidth)cacheLabel=counts;
        fit(g,cacheLabel,cacheX,p.y()+11,cacheWidth,MUTED);
        if(!author.isEmpty())text(g,author,authorX,p.y()+11,MUTED);
        if(overlayOpen()) {
            drawModal(g,mx,my);
            super.extractRenderState(g,mx,my,delta);
            return;
        }
        int count=Math.max(0,MoonotesNetworkClient.collectionCount());
        int cached=Math.max(0,MoonotesNetworkClient.cachedCount());
        updateButtonStates(count,cached);
        g.enableScissor(layout.contentX-1,layout.contentTop-1,layout.contentX+layout.contentWidth+1,layout.contentBottom+1);
        switch(tab) {
            case 0 -> drawLibrary(g,mx,my,count,cached);
            case 1 -> drawChannels(g,count,cached);
            case 2 -> drawDownloads(g,MoonotesNetworkClient.collectionImageCount(),MoonotesNetworkClient.cachedImageCount(),mx,my);
            default -> drawSettings(g,mx,my);
        }
        g.disableScissor();
        super.extractRenderState(g,mx,my,delta);
    }

    private void updateButtonStates(int count,int cached) {
        boolean busy=MoonotesNetworkClient.isDownloading();
        if(tab==2 && startButton!=null) {
            startButton.active=!busy && !CacheMaintenance.busy() && count>cached;
            String text=busy?"Идёт загрузка":count<=cached?"Всё скачано":"Добрать отсутствующие";
            if (!startButton.getMessage().getString().equals(text)) startButton.setMessage(Component.literal(text));
        }
        if(tab==2 && pauseButton!=null) {
            pauseButton.active=busy || (count>cached && !CacheMaintenance.busy());
            String text=busy?(MoonotesNetworkClient.isPaused()?"Продолжить":"Пауза"):"Проверить источники";
            if (!pauseButton.getMessage().getString().equals(text)) pauseButton.setMessage(Component.literal(text));
        }
        if(tab==2 && stopButton!=null) {
            stopButton.active=!CacheMaintenance.busy() || busy;
            String text=busy?"Остановить":"Кеш и проверка";
            if (!stopButton.getMessage().getString().equals(text)) stopButton.setMessage(Component.literal(text));
        }
        if(refreshButton!=null) refreshButton.active=!MoonotesNetworkClient.isManifestLoading();
        if(reportButton!=null)reportButton.active=MoonotesNetworkClient.localDiagnostics();
        if(startButton!=null && CacheMaintenance.busy())startButton.active=false;
        if(healthButton!=null) {
            var health=StreamotesBridge.health();
            boolean ok=health.good() && health.expected()>0 && MoonotesNetworkClient.cachedCount()>=MoonotesNetworkClient.collectionCount();
            healthButton.setMessage(Component.literal(layout.wide?(ok?"● Готово":"● Проверить"):(ok?"● OK":"● !")));
            ((MoonotesButton)healthButton).setLabelColor(ok?GREEN:WARNING);
        }
    }

    private void drawLibrary(GuiGraphicsExtractor g,int mx,int my,int count,int cached) {
        var grid=configureGrid();
        for(int row=0;row<grid.rows();row++)for(int col=0;col<grid.columns();col++){
            int position=(libraryScroll.row+row)*grid.columns()+col;
            if(position>=filtered.size())continue;
            int idx=filtered.get(position);
            var card=grid.card(col,row);
            boolean hovered=card.contains(mx,my),ready=MoonotesNetworkClient.itemCached(idx);
            int border=hovered||keyboardPosition==position?MoonotesTheme.ACCENT:MoonotesTheme.LINE;
            if(hovered)g.requestCursor(CursorTypes.POINTING_HAND);
            g.fill(card.x(),card.y(),card.right(),card.bottom(),border);
            g.fill(card.x()+1,card.y()+1,card.right()-1,card.bottom()-1,hovered?MoonotesTheme.HOVER:MoonotesTheme.CARD);
            int previewX=card.x()+4,previewY=card.y()+4,previewSide=Math.min(27,card.height()-8);
            int detailY=card.y()+Math.max(15,card.height()-14);
            g.fill(previewX,previewY,previewX+previewSide,previewY+previewSide,0xFF152136);
            if(ready)previews.draw(g,MoonotesNetworkClient.itemId(idx),previewX,previewY,previewSide);
            else centered(g,"…",previewX+previewSide/2,previewY+previewSide/2-4,previewSide,MUTED);
            String name=MoonotesNetworkClient.itemName(idx);
            fit(g,name,card.x()+37,card.y()+4,card.width()-42,ready?TEXT:MUTED);
            fit(g,ready?size(MoonotesNetworkClient.itemBytes(idx)):"Не загружен",
                card.x()+37,detailY,card.width()-58,ready?GREEN:MUTED);
            text(g,preferences.favorite(MoonotesNetworkClient.itemKey(idx))?"★":"☆",card.right()-14,detailY,preferences.favorite(MoonotesNetworkClient.itemKey(idx))?0xFFFFD788:MUTED);
        }
        drawScrollbar(g,grid.scrollbar(),libraryScroll,mx,my);
        String range=filtered.isEmpty()?"0 результатов":"Показано "+grid.firstVisible(libraryScroll.row,filtered.size())+"–"+grid.lastVisible(libraryScroll.row,filtered.size())+" из "+filtered.size();
        fit(g,range,layout.contentX,layout.librarySummaryY(),layout.contentWidth,MUTED);
        int hovered=grid.positionAt(mx,my,libraryScroll.row,filtered.size());
        if(hovered>=0){
            int idx=filtered.get(hovered);
            var card=grid.card(hovered%grid.columns(),hovered/grid.columns()-libraryScroll.row);
            boolean overStar=layout.libraryStar(card).contains(mx,my);
            tooltip(g,(overStar?"star:":"emote:")+MoonotesNetworkClient.itemKey(idx),MoonotesNetworkClient.itemName(idx)
                +(overStar?(preferences.favorite(MoonotesNetworkClient.itemKey(idx))?"\nУбрать из избранного":"\nДобавить в избранное")
                    :MoonotesNetworkClient.itemCached(idx)?"\nОткрыть предпросмотр и действия с эмотом.":"\nИзображение пока не в кеше. Название можно скопировать."),mx,my);
        } else if(grid.scrollbar().contains(mx,my) && libraryScroll.maximum()>0){
            tooltip(g,"library-scroll","Потяните ползунок или нажмите на дорожку.\nТакже работают колесо мыши, Page Up/Down и Home/End.",mx,my);
        }else hoveredTarget="";
        if(filtered.isEmpty()){
            centered(g,count==0?(MoonotesNetworkClient.catalogBusy()?"Загружаем каталог…":"В выбранных каталогах нет эмотов"):"Ничего не найдено",
                layout.contentX+layout.contentWidth/2,grid.viewport().y()+12,layout.contentWidth-20,MUTED);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g,MoonotesLayout.Rect track,ScrollState state,int mx,int my){
        if(state.maximum()<=0)return;
        g.fill(track.x()+3,track.y(),track.right()-3,track.bottom(),0xFF0E1B2D);
        if(track.contains(mx,my))g.requestCursor(CursorTypes.POINTING_HAND);
        int thumbTop=state.thumbTop(),thumbHeight=state.thumbHeight();
        int color=state.dragging()?0xFFC0DCFF:track.contains(mx,my)?0xFF9DC8FF:0xFF6F99D0;
        g.fill(track.x()+1,thumbTop,track.right()-1,thumbTop+thumbHeight,color);
        if(thumbHeight>=22){
            int middle=thumbTop+thumbHeight/2;
            g.fill(track.x()+4,middle-2,track.right()-4,middle-1,0xFF334D70);
            g.fill(track.x()+4,middle+1,track.right()-4,middle+2,0xFF334D70);
        }
    }

    private void drawChannels(GuiGraphicsExtractor g,int count,int cached){
        configureCatalogs();
        String status=MoonotesNetworkClient.catalogStatus();
        if(!status.isBlank()) fit(g,status,layout.contentX,layout.contentTop+26,layout.contentWidth,MoonotesNetworkClient.catalogErrorDetails().isBlank()?MUTED:WARNING);
        else fit(g,MoonotesNetworkClient.serverProfilesEnabled() ? "Выбор каталога сохраняется для этого сервера" : "Общий выбор каталога для всех серверов",
            layout.contentX,layout.contentTop+26,layout.contentWidth,MUTED);
        var catalogs=MoonotesNetworkClient.catalogs();
        for(int row=0;row<catalogRows;row++) {
            int index=catalogScroll.row+row;if(index>=catalogs.size())break;
            var catalog=catalogs.get(index);
            int y=catalogTop+row*48;
            fit(g,"Кеш "+catalog.cached()+"/"+catalog.total()+" · "+(catalog.enabled()?"В библиотеке":"Скрыт")+" · +"+catalog.added()+" / −"+catalog.removed(),
                layout.contentX+6,y+29,layout.contentWidth-31,catalog.enabled()?MUTED:WARNING);
        }
        drawScrollbar(g,catalogTrack,catalogScroll,-1,-1);
    }

    private void drawDownloads(GuiGraphicsExtractor g,int count,int cached,int mx,int my){
        int x=layout.contentX,y=layout.contentTop,w=layout.contentWidth;
        count=Math.max(0,count);cached=Math.max(0,Math.min(count,cached));
        boolean busy=MoonotesNetworkClient.isDownloading();
        fit(g,MoonotesNetworkClient.selectedCatalogLabel()+" · "+MoonotesNetworkClient.stageLabel(),x,y+6,w-116,TEXT);
        double part=count==0?0:Math.min(1,cached/(double)count);
        var bar=layout.downloadProgress();
        g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xFF263246);
        g.fill(bar.x(),bar.y(),bar.x()+(int)Math.round(w*part),bar.bottom(),MoonotesNetworkClient.isPaused()?0xFFD5AD6E:BLUE);
        fit(g,"Готово "+cached+"/"+count+" файлов · "+(int)(part*100)+"% · Осталось "+Math.max(0,count-cached),x,y+44,w,part>=1?GREEN:TEXT);
        int available=layout.contentBottom-y;
        String speed=size((long)MoonotesNetworkClient.speedBytesPerSecond())+"/с";
        if(available<190){
            fit(g,"Сеть: "+speed+" · Получено "+size(MoonotesNetworkClient.receivedBytes()),x,y+63,w,BLUE);
            fit(g,"За запуск: "+MoonotesNetworkClient.downloaded()+" · Ошибок: "+MoonotesNetworkClient.failed(),x,y+80,w,MUTED);
            if(available>=108)fit(g,"На диске: "+size(MoonotesNetworkClient.channelCacheSize()),x,y+97,w,MUTED);
            if(available>=125)fit(g,"Источник: "+MoonotesNetworkClient.lastDownloadSource(),x,y+114,w,MUTED);
            if(available>=142)fit(g,processingLabel(),x,y+131,w,MUTED);
            if(available>=159)fit(g,busy?"Сейчас: "+MoonotesNetworkClient.currentEmote():"Не хватает: "+MoonotesNetworkClient.firstMissingSummary(),x,y+148,w,busy?TEXT:WARNING);
        }else{
            int statWidth=(w-12)/3;
            statistic(g,x,y+64,statWidth,"Скорость сети",speed,BLUE);
            statistic(g,x+statWidth+6,y+64,statWidth,"Получено",size(MoonotesNetworkClient.receivedBytes()),TEXT);
            statistic(g,x+(statWidth+6)*2,y+64,statWidth,"Ошибки",String.valueOf(MoonotesNetworkClient.failed()),MoonotesNetworkClient.failed()>0?WARNING:GREEN);
            fit(g,"Новых файлов: "+MoonotesNetworkClient.downloaded()+" · Уже было: "+MoonotesNetworkClient.alreadyCached(),x,y+119,w,TEXT);
            fit(g,processingLabel(),x,y+138,w,MUTED);
            fit(g,"На диске: "+size(MoonotesNetworkClient.channelCacheSize())+" · Общий кеш: "+size(MoonotesNetworkClient.cacheSize()),x,y+157,w,MUTED);
            String origin="Источник: "+MoonotesNetworkClient.lastDownloadSource();
            if(MoonotesNetworkClient.fallbackRemainingSeconds()>0)origin+=" · Повторная проверка 7TV через "+MoonotesNetworkClient.fallbackRemainingSeconds()+" с";
            fit(g,origin,x,y+176,w,MUTED);
            if(available>=214)fit(g,"Сейчас: "+(MoonotesNetworkClient.currentEmote().isBlank()?"—":MoonotesNetworkClient.currentEmote()),x,y+195,w,TEXT);
            if(!busy && cached<count && available>=239){
                boolean room=available>=287;
                if(room)g.fill(x,y+215,x+w,y+283,MoonotesTheme.CARD);
                int inset=room?8:0;
                fit(g,"Нет в кеше: "+MoonotesNetworkClient.firstMissingSummary(),x+inset,y+223,w-inset*2,WARNING);
                String missingId=MoonotesNetworkClient.firstMissingId();
                String failure=MoonotesNetworkClient.failedSourcesFor(missingId);
                if(room && missingId.equals(CacheMaintenance.probedId()))fit(g,"7TV: "+CacheMaintenance.direct()+" · Cloudflare: "+CacheMaintenance.alternative(),x+8,y+242,w-16,MUTED);
                else if(room && !failure.isBlank())fit(g,failure,x+8,y+242,w-16,MUTED);
                else if(room)fit(g,"«Проверить источники» покажет причину ошибки",x+8,y+242,w-16,MUTED);
                if(room)fit(g,"Исправные файлы останутся в кеше.",x+8,y+261,w-16,BLUE);
            }
            if(rect(x,y+64,statWidth,42).contains(mx,my))tooltip(g,"network-speed",
                "Полученные байты за последние секунды.\nДекодирование изображений и запись кеша выполняются отдельно; в этот момент скорость сети может быть нулевой.",mx,my);
            else hoveredTarget="";
        }
    }
    private String processingLabel(){
        return "Запросов: "+MoonotesNetworkClient.activeTransfers()+" · Ожидают обработки: "+MoonotesNetworkClient.queuedDownloads();
    }
    private void statistic(GuiGraphicsExtractor g,int x,int y,int w,String label,String value,int color){
        g.fill(x,y,x+w,y+42,MoonotesTheme.CARD);
        fit(g,label,x+7,y+7,w-14,MUTED);
        fit(g,value,x+7,y+24,w-14,color);
    }

    private void drawSettings(GuiGraphicsExtractor g,int mx,int my){
        var side=layout.settingsSidebar();
        g.fill(side.x(),side.y(),side.right(),side.bottom(),MoonotesTheme.PANEL);
        fit(g,"РАЗДЕЛЫ",side.x()+8,side.y()+4,side.width()-16,MUTED);
        var heading=layout.settingsHeading();
        fit(g,SETTING_SECTIONS[settingsSection],heading.x(),heading.y(),heading.width(),TEXT);
        fit(g,"Сохраняется автоматически",heading.x(),heading.y()+16,heading.width(),MUTED);
        configureSettings();var grid=layout.settingsGrid(SETTING_OPTIONS[settingsSection].length);int[] options=SETTING_OPTIONS[settingsSection];boolean hover=false;
        for(int row=0;row<settingRows;row++)for(int col=0;col<settingColumns;col++){
            int index=(settingsScroll.row+row)*settingColumns+col;if(index>=options.length)continue;
            int option=options[index];var card=grid.card(col,row);
            g.fill(card.x(),card.y(),card.right(),card.bottom(),MoonotesTheme.CARD);
            fit(g,settingTitle(option),card.x()+7,card.y()+5,card.width()-14,MUTED);
            if(rect(card.x(),card.y(),card.width(),16).contains(mx,my)){
                tooltip(g,"setting:"+option,settingTitle(option)+"\n"+settingHint(option),mx,my);hover=true;
            }
        }
        drawScrollbar(g,settingsTrack,settingsScroll,mx,my);
        if(!hover)hoveredTarget="";
    }

    private void tooltip(GuiGraphicsExtractor g,String target,String message,int x,int y){
        long now=System.nanoTime();
        if(!target.equals(hoveredTarget)){hoveredTarget=target;hoverSince=now;}
        if(now-hoverSince>=TOOLTIP_DELAY)g.setTooltipForNextFrame(font,
            font.split(Component.literal(message),Math.max(80,Math.min(280,width-24))),x,y);
    }

    private void openEmote(int index){
        libraryScroll.release();
        clearFocus();
        selectedId=MoonotesNetworkClient.itemId(index);
        selectedName=MoonotesNetworkClient.itemName(index);
        selectedCached=MoonotesNetworkClient.itemCached(index);
        selectedBytes=MoonotesNetworkClient.itemBytes(index);
        previews.closeModal();
        copiedUntil=0;noticeUntil=0;
        rebuildMoonotesWidgets();
    }
    private void openReport(){
        releaseNotesOpen=false;eventsOpen=false;
        reportOpen=true;reportScroll.row=0;reportScroll.release();copiedUntil=0;
        report=MoonotesNetworkClient.diagnosticsReport(observedMouseClicks,observedMouseDrags,"",selectedId.isEmpty()?-1:0);
        if(MoonotesNetworkClient.localDiagnostics())report+=StreamotesBridge.diagnostics()+"\n"+CacheMaintenance.report();
        reportLines.clear();
        for(String line:report.split("\\R"))reportLines.add(line);
        clearFocus();
        rebuildMoonotesWidgets();
    }
    private void openEventLog(){
        releaseNotesOpen=false;eventsOpen=true;reportOpen=true;
        var records=MoonotesNotifications.history();
        var buffer=new StringBuilder("Последние события хранятся только локально до закрытия игры.\n");
        if(records.isEmpty())buffer.append("Пока событий нет.\n");
        java.time.format.DateTimeFormatter formatter=java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        for(var event:records){
            String time=java.time.Instant.ofEpochMilli(event.created())
                .atZone(java.time.ZoneId.systemDefault()).format(formatter);
            buffer.append(time).append(event.error()?" [!] ":" [i] ")
                .append(event.title()).append(": ").append(event.message()).append('\n');
        }
        report=buffer.toString();reportLines.clear();
        for(String line:report.split("\\R"))reportLines.add(line);
        reportScroll.row=0;rebuildMoonotesWidgets();
    }

    private void openReleaseNotes(){
        releaseNotesOpen=true;eventsOpen=false;
        reportOpen=true;reportScroll.row=0;reportScroll.release();
        report=String.join("\n",
            "Moonotes Network 0.5.3 — загрузка каталогов и управление",
            "",
            "ЗАГРУЗКА",
            "• Исправлены адреса изображений новых каталогов: 7TV получает канонический идентификатор, общий кеш сохраняет прежние имена файлов.",
            "• Для загрузки через Cloudflare нужен Worker 0.5.3. Готовые изображения повторно скачивать не нужно.",
            "",
            "ИНТЕРФЕЙС",
            "• Разделы настроек перенесены в боковую панель. Они больше не выглядят как вторая строка главного меню.",
            "• Поля поиска и ввода оформлены в цвет интерфейса; фокус выделяется светлой рамкой.",
            "• Кнопки у чата получили текстовые подписи: «Эмоты» и «Moonotes».",
            "",
            "МИНИ-БИБЛИОТЕКА И ГРУППЫ",
            "• Создание и переименование группы отделены от поиска. Название вводится в отдельном поле.",
            "• Кнопки избранного и меню группы имеют раздельную подсветку и подсказки.",
            "• «Вставить в чат» добавляет название к сообщению без автоматической отправки.",
            "",
            "УВЕДОМЛЕНИЯ",
            "• Автоматические сообщения Streamotes могут идти в общей очереди с Moonotes и не перекрывают друг друга.",
            "• Переключатель находится в «Настройки → Вид → Уведомления Streamotes».",
            "• Отключённые в самом Streamotes сообщения остаются отключёнными. Другие уведомления Minecraft работают как прежде.",
            "",
            "Каталоги, группы, избранное и общий кеш сохраняются. Streamotes и основной Moonotes остаются отдельными модами.");
        reportLines.clear();
        for(String line:report.split("\\R"))reportLines.add(line);
        MoonotesNetworkClient.markReleaseNotesSeen();
        rebuildMoonotesWidgets();
    }

    private void closeOverlay(){
        reportOpen=false;releaseNotesOpen=false;eventsOpen=false;selectedId="";selectedName="";report="";
        catalogFormOpen=catalogPickerOpen=cacheOpen=removeConfirm=catalogPreviewOpen=false;
        catalogErrorReport=false;MoonotesNetworkClient.cancelCatalogPreview();
        importGeneration++;fileImportBusy=false;
        catalogDetailKey="";importTarget="";formValue="";pickerScroll.release();
        reportScroll.release();
        reportLines.clear();previews.closeModal();copiedUntil=0;
        rebuildMoonotesWidgets();
    }

    private void buildModalButtons(){
        if(catalogPreviewOpen){buildCatalogPreview();return;}
        if(catalogFormOpen){buildCatalogForm();return;}
        if(catalogPickerOpen){buildCatalogPicker();return;}
        if(!catalogDetailKey.isEmpty()){buildCatalogDetail();return;}
        if(cacheOpen){buildCacheModal();return;}
        var box=modalBox();
        int w=(box.width()-30)/2;
        if(!reportOpen) {
            button(rect(box.x()+12,box.bottom()-60,w,22),"Вставить в чат","Открыть чат и добавить эмот к черновику в формате Streamotes (в том числе :название:). Отправка выполняется только вручную.",true,b->{
                try { ChatInsertion.open(selectedName);preferences.used(selectedId+":"+selectedName); }
                catch(IllegalArgumentException ex){statusNotice=ex.getMessage();noticeUntil=System.currentTimeMillis()+5000;}
            });
            button(rect(box.x()+18+w,box.bottom()-60,w,22),preferences.favorite(selectedId+":"+selectedName)?"★ В избранном":"☆ В избранное",
                "Сохранить смайлик в избранном. Настройка хранится только локально.",false,b->{preferences.toggle(selectedId+":"+selectedName);filterDirty=true;rebuildMoonotesWidgets();});
        }
        copyButton=button(rect(box.x()+12,box.bottom()-32,w,22),
            reportOpen?(releaseNotesOpen?"Скопировать текст":"Скопировать отчёт"):"Скопировать название",
            reportOpen?(releaseNotesOpen?"Скопировать список изменений.":"Скопировать показанный отчёт в буфер обмена. Отправить его можно только вручную.")
                :"Скопировать текст смайлика для вставки в чат.",true,b->{
                Minecraft.getInstance().keyboardHandler.setClipboard(reportOpen?report:selectedName);
                copiedUntil=System.currentTimeMillis()+2000;
                if(!reportOpen)preferences.used(selectedId+":"+selectedName);
            });
        button(rect(box.x()+18+w,box.bottom()-32,w,22),"Закрыть",
            "Вернуться к прежнему месту в библиотеке или настройках.",false,b->closeOverlay());
    }

    private void drawModal(GuiGraphicsExtractor g,int mx,int my){
        g.nextStratum();
        g.fill(layout.panel.x()+1,layout.panel.y()+30,layout.panel.right()-1,layout.panel.bottom()-1,0xCC09111E);
        var box=modalBox();
        g.fill(box.x()-1,box.y()-1,box.right()+1,box.bottom()+1,0xFF6C8EB9);
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xFF1C2C43);
        g.fill(box.x(),box.y(),box.right(),box.y()+25,0xFF2A405E);
        String modalTitle=catalogPreviewOpen?"Проверка каталога":catalogFormOpen?(importTarget.isEmpty()?"Добавить каталог":"Обновить JSON каталога"):catalogPickerOpen?"Показывать в библиотеке":!catalogDetailKey.isEmpty()?"Управление каталогом":cacheOpen?"Кеш и проверка":reportOpen?(releaseNotesOpen?"Что нового в 0.5.3":eventsOpen?"События Moonotes":catalogErrorReport?"Проверка подключения":"Локальный отчёт"):"Просмотр смайлика";
        text(g,modalTitle,box.x()+12,box.y()+9,TEXT);
        if(catalogPreviewOpen){drawCatalogPreview(g,box);return;}
        if(catalogFormOpen){drawCatalogForm(g,box);return;}
        if(catalogPickerOpen){drawScrollbar(g,pickerTrack(),pickerScroll,mx,my);return;}
        if(!catalogDetailKey.isEmpty()){drawCatalogDetail(g,box);return;}
        if(cacheOpen){drawCacheModal(g,box);return;}
        if(copyButton!=null)copyButton.setMessage(Component.literal(System.currentTimeMillis()<copiedUntil?"Скопировано":reportOpen?(releaseNotesOpen?"Скопировать текст":"Скопировать отчёт"):"Скопировать название"));
        if(reportOpen){
            int startY=box.y()+34,rows=configureReportScroll();
            g.enableScissor(box.x()+10,startY-1,box.right()-10,box.bottom()-41);
            for(int line=0;line<rows && reportScroll.row+line<wrappedReportLines.size();line++)
                g.text(font,wrappedReportLines.get(reportScroll.row+line),box.x()+12,startY+line*12,MUTED,false);
            g.disableScissor();
            drawScrollbar(g,reportTrack(),reportScroll,mx,my);
        }else{
            centered(g,selectedName,box.x()+box.width()/2,box.y()+35,box.width()-24,TEXT);
            int previewSize=Math.max(36,Math.min(144,box.height()-150));
            int px=box.x()+(box.width()-previewSize)/2,py=box.y()+53;
            g.fill(px-4,py-4,px+previewSize+4,py+previewSize+4,0xFF111D2E);
            int frames=selectedCached?previews.drawDetailed(g,selectedId,px,py,previewSize):0;
            if(!selectedCached)centered(g,"Не загружен",px+previewSize/2,py+previewSize/2-4,previewSize,MUTED);
            String info=selectedCached?size(selectedBytes)+" · "+(frames>1?"Анимация":frames==1?"Статичный":
                previews.detailUnavailable()?"Превью недоступно":"Подготовка превью…"):"Изображение появится после загрузки";
            if(System.currentTimeMillis()<noticeUntil && !statusNotice.isEmpty())info=statusNotice;
            centered(g,info,box.x()+box.width()/2,py+previewSize+12,box.width()-20,selectedCached?GREEN:MUTED);
        }
    }

    private int configureReportScroll(){
        var box=layout.modal(true);
        int rows=Math.max(1,(box.height()-82)/12);
        int wrapWidth=Math.max(60,box.width()-40);
        if(reportWrapWidth!=wrapWidth || !wrappedReport.equals(report)){
            wrappedReportLines.clear();
            for(String line:report.split("\\R",-1)){
                if(line.isEmpty())wrappedReportLines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                else wrappedReportLines.addAll(font.split(Component.literal(line),wrapWidth));
            }
            wrappedReport=report;reportWrapWidth=wrapWidth;
        }
        reportScroll.configure(box.y()+34,rows*12,rows,Math.max(0,wrappedReportLines.size()-rows));
        return rows;
    }
    private MoonotesLayout.Rect reportTrack(){
        var box=layout.modal(true);
        return rect(box.right()-18,box.y()+34,13,Math.max(1,(box.height()-82)/12)*12);
    }
    private boolean dispatchWidgetClick(MouseButtonEvent event,boolean doubleClick){
        boolean handled=super.mouseClicked(event,doubleClick);
        // A callback may rebuild children. Never leave an invisible old widget focused.
        if(getFocused()!=null && !children().contains(getFocused())){clearFocus();setDragging(false);}
        return handled;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(MoonotesNetworkClient.localDiagnostics())observedMouseClicks++;
        if(overlayOpen()){
            if(catalogPickerOpen && MoonotesInput.isPrimary(event)){
                configurePicker();
                if(pickerScroll.maximum()>0 && pickerTrack().contains(event.x(),event.y())){
                    clearFocus();pickerScroll.press(event.y());rebuildMoonotesWidgets();return true;
                }
            }
            if(reportOpen && MoonotesInput.isPrimary(event)){
                configureReportScroll();
                if(reportScroll.maximum()>0 && reportTrack().contains(event.x(),event.y())){
                    clearFocus();reportScroll.press(event.y());return true;
                }
            }
            if(MoonotesInput.isPrimary(event) && !modalBox().contains(event.x(),event.y())){
                closeOverlay();return true;
            }
            dispatchWidgetClick(event,doubleClick);
            return true;
        }
        if(MoonotesInput.isPrimary(event)){
            if(tab==0){
                var grid=configureGrid();
                if(libraryScroll.maximum()>0 && grid.scrollbar().contains(event.x(),event.y())){
                    clearFocus();libraryScroll.press(event.y());return true;
                }
                int position=grid.positionAt(event.x(),event.y(),libraryScroll.row,filtered.size());
                if(position>=0){
                    int index=filtered.get(position);
                    var card=grid.card(position%grid.columns(),position/grid.columns()-libraryScroll.row);
                    if(layout.libraryStar(card).contains(event.x(),event.y())) {
                        preferences.toggle(MoonotesNetworkClient.itemKey(index));filterDirty=true;
                    }else openEmote(index);
                    return true;
                }
            }else if(tab==1){
                configureCatalogs();
                if(catalogScroll.maximum()>0 && catalogTrack.contains(event.x(),event.y())){
                    clearFocus();catalogScroll.press(event.y());rebuildMoonotesWidgets();return true;
                }
            }else if(tab==3){
                configureSettings();
                if(settingsScroll.maximum()>0 && settingsTrack.contains(event.x(),event.y())){
                    clearFocus();settingsScroll.press(event.y());rebuildMoonotesWidgets();return true;
                }
            }
        }
        return dispatchWidgetClick(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(MoonotesNetworkClient.localDiagnostics())observedMouseDrags++;
        if(reportOpen && MoonotesInput.isPrimary(event) && reportScroll.drag(event.y()))return true;
        if(catalogPickerOpen && MoonotesInput.isPrimary(event) && pickerScroll.drag(event.y())){rebuildMoonotesWidgets();return true;}
        if(overlayOpen())return super.mouseDragged(event,dx,dy);
        if(MoonotesInput.isPrimary(event)){
            if(libraryScroll.drag(event.y()))return true;
            if(settingsScroll.drag(event.y())){rebuildMoonotesWidgets();return true;}
            if(catalogScroll.drag(event.y())){rebuildMoonotesWidgets();return true;}
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(MoonotesInput.isPrimary(event)){
            boolean library=libraryScroll.release(),settings=settingsScroll.release(),report=reportScroll.release();
            boolean catalogs=catalogScroll.release(),picker=pickerScroll.release();
            boolean handled=super.mouseReleased(event);
            return library||settings||report||catalogs||picker||overlayOpen()||handled;
        }
        boolean handled=super.mouseReleased(event);
        return overlayOpen() || handled;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical==0)return super.mouseScrolled(x,y,horizontal,vertical);
        int direction=vertical>0?-1:1;
        if(reportOpen){
            configureReportScroll();reportScroll.move(direction*3);return true;
        }
        if(catalogPickerOpen){pickerScroll.move(direction*3);rebuildMoonotesWidgets();return true;}
        if(catalogFormOpen)return super.mouseScrolled(x,y,horizontal,vertical);
        if(overlayOpen())return true;
        if(tab==0 && (layout.libraryGrid().viewport().contains(x,y) || layout.libraryGrid().scrollbar().contains(x,y))){
            configureGrid();libraryScroll.move(direction*3);keyboardPosition=-1;return true;
        }
        if(tab==1 && x>=layout.contentX && x<layout.contentX+layout.contentWidth && y>=catalogTop && y<=layout.contentBottom){
            configureCatalogs();catalogScroll.move(direction);rebuildMoonotesWidgets();return true;
        }
        if(tab==3 && x>=layout.contentX && x<layout.contentX+layout.contentWidth && y>=settingsTop && y<=layout.contentBottom){
            configureSettings();settingsScroll.move(direction);rebuildMoonotesWidgets();return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(overlayOpen() && event.isEscape()){closeOverlay();return true;}
        if(!overlayOpen() && tab==0 && (searchField==null || !searchField.isFocused())){
            var grid=configureGrid();
            int key=event.key();
            if(key==InputConstants.KEY_PAGEUP){libraryScroll.move(-grid.rows());return true;}
            if(key==InputConstants.KEY_PAGEDOWN){libraryScroll.move(grid.rows());return true;}
            if(key==InputConstants.KEY_HOME){libraryScroll.row=0;return true;}
            if(key==InputConstants.KEY_END){libraryScroll.row=libraryScroll.maximum();return true;}
            if(key==InputConstants.KEY_UP || key==InputConstants.KEY_DOWN){libraryScroll.move(key==InputConstants.KEY_UP?-1:1);return true;}
        }
        return super.keyPressed(event);
    }
    private MoonotesLayout.Rect modalBox(){
        return layout.modal(reportOpen || catalogFormOpen || catalogPickerOpen || cacheOpen || catalogPreviewOpen || !catalogDetailKey.isEmpty());
    }
    @Override public void tick(){
        super.tick();
        var pendingPreview=MoonotesNetworkClient.catalogPreview();
        if(pendingPreview!=null && pendingPreview.token()!=observedPreviewToken){
            observedPreviewToken=pendingPreview.token();catalogPreviewOpen=true;catalogFormOpen=false;catalogDetailKey="";
            clearFocus();rebuildMoonotesWidgets();
        }
        long revision=MoonotesNetworkClient.catalogRevision();
        if(revision!=observedRevision){
            observedRevision=revision;filterDirty=true;
            if(!catalogFormOpen)rebuildMoonotesWidgets();
        }
        if(submitCatalogButton!=null)submitCatalogButton.active=!MoonotesNetworkClient.catalogBusy() && !fileImportBusy && !formValue.isBlank();
        if(quickAddButton!=null)quickAddButton.active=validQuickChannel() && !MoonotesNetworkClient.catalogBusy();
        if(catalogDetailsButton!=null)catalogDetailsButton.active=!MoonotesNetworkClient.catalogErrorDetails().isBlank();
        if(previewConfirmButton!=null)previewConfirmButton.active=!MoonotesNetworkClient.catalogBusy();
        if(repairButton!=null){
            repairButton.active=!MoonotesNetworkClient.isDownloading();
            repairButton.setMessage(Component.literal(CacheMaintenance.busy()?"Остановить проверку":"Проверить и исправить"));
        }
    }
    private void configureCatalogs(){
        catalogTop=layout.contentTop+42;
        catalogRows=Math.max(1,(layout.contentBottom-catalogTop+6)/48);
        catalogTrack=rect(layout.contentX+layout.contentWidth-13,catalogTop,13,catalogRows*48-6);
        catalogScroll.configure(catalogTop,catalogTrack.height(),catalogRows,Math.max(0,MoonotesNetworkClient.catalogs().size()-catalogRows));
    }
    private void buildCatalogWidgets(){
        configureCatalogs();
        int x=layout.contentX,y=layout.contentTop+2,w=layout.contentWidth;
        int addW=Math.max(92,Math.min(130,w/4));
        quickChannelField=addRenderableWidget(new MoonotesEditBox(font,x,y,w-addW-6,22,Component.literal("Канал Twitch")));
        quickChannelField.setHint(Component.literal("Ник Twitch, например v0kky"));
        quickChannelField.setMaxLength(25);
        quickChannelField.setValue(quickChannel);
        quickChannelField.setResponder(value->{quickChannel=value; if(quickAddButton!=null)
            quickAddButton.active=validQuickChannel() && !MoonotesNetworkClient.catalogBusy();});
        quickChannelField.setTooltip(Tooltip.create(Component.literal("Добавить канал прямо из GUI. Введите логин Twitch без @ или ссылки.")));
        quickAddButton=button(rect(x+w-addW,y,addW,22),"+ Канал",
            "Найти набор 7TV по Twitch-нику. Перед добавлением вы увидите название и количество эмотов. Картинки скачиваются отдельно.",true,b->submitQuickChannel());
        quickAddButton.active=validQuickChannel() && !MoonotesNetworkClient.catalogBusy();
        var catalogs=MoonotesNetworkClient.catalogs();
        for(int row=0;row<catalogRows;row++){
            int index=catalogScroll.row+row;if(index>=catalogs.size())break;
            var c=catalogs.get(index);
            button(rect(layout.contentX,catalogTop+row*48,layout.contentWidth-18,24),
                c.name()+" · "+(c.kind().equals("channel")?"Канал":c.kind().equals("set")?"Набор 7TV":"JSON"),
                "Открыть настройки: библиотека, чат в этом мире, обновление и удаление каталога.",false,b->{
                    catalogDetailKey=c.key();removeConfirm=false;rebuildMoonotesWidgets();
                });
        }
        button(layout.footer(0,3),"Набор / JSON","Добавить набор по ID либо импортировать свой JSON. Для Twitch-ника используйте поле сверху.",true,b->openCatalogForm(1,""));
        catalogDetailsButton=button(layout.footer(1,3),"Подробности","Показать источник, этап запроса и код последней ошибки. Можно скопировать вручную.",false,b->openCatalogDiagnostics());
        catalogDetailsButton.active=!MoonotesNetworkClient.catalogErrorDetails().isBlank();
        button(layout.footer(2,3),"В библиотеку","Вернуться к эмотам. Каталог выбирается рядом с поиском.",false,b->switchTab(0));
    }
    private boolean validQuickChannel(){return quickChannel!=null && quickChannel.matches("[a-zA-Z0-9_]{1,25}");}
    private void submitQuickChannel(){
        if(!validQuickChannel() || MoonotesNetworkClient.catalogBusy()) return;
        String login=quickChannel.toLowerCase(Locale.ROOT);
        MoonotesNetworkClient.beginCatalogPreview("channel",login);
        statusNotice="Запрашиваем каталог для "+login+"…";
        noticeUntil=System.currentTimeMillis()+5000;
        rebuildMoonotesWidgets();
    }
    private void buildCatalogPreview(){
        var preview=MoonotesNetworkClient.catalogPreview();
        if(preview==null){catalogPreviewOpen=false;rebuildMoonotesWidgets();return;}
        var box=modalBox();int x=box.x()+12,w=(box.width()-30)/2;
        boolean exists=MoonotesNetworkClient.catalogs().stream().anyMatch(c->c.key().equals(preview.kind()+":"+preview.reference()));
        previewConfirmButton=button(rect(x,box.bottom()-32,w,22),exists?"Обновить каталог":"Добавить каталог",
            "Сохранить найденный набор для работы без сети. Загрузка изображений запускается отдельно.",true,b->{
                if(MoonotesNetworkClient.confirmCatalogPreview(preview.token())){
                    catalogPreviewOpen=false;quickChannel="";tab=1;rebuildMoonotesWidgets();
                }
            });
        previewConfirmButton.active=!MoonotesNetworkClient.catalogBusy();
        button(rect(x+w+6,box.bottom()-32,w,22),"Отмена","Закрыть предпросмотр. Каталог не будет добавлен.",false,b->closeOverlay());
    }
    private void drawCatalogPreview(GuiGraphicsExtractor g,MoonotesLayout.Rect box){
        var preview=MoonotesNetworkClient.catalogPreview();if(preview==null)return;
        int x=box.x()+12,y=box.y()+35,w=box.width()-24;
        fit(g,preview.name(),x,y,w,TEXT);
        fit(g,(preview.kind().equals("channel")?"Twitch: ":"Набор: ")+preview.reference(),x,y+19,w,MUTED);
        fit(g,"Названий: "+preview.names()+" · Файлов: "+preview.files(),x,y+38,w,GREEN);
        fit(g,"Получен через: "+preview.source(),x,y+57,w,MUTED);
        if(box.height()>=230)fit(g,"Набор 7TV: "+preview.setId(),x,y+76,w,MUTED);
        int sampleY=y+(box.height()>=230?100:80);
        if(!preview.sample().isEmpty()){
            var samples=font.split(Component.literal(String.join(" · ",preview.sample())),w);
            for(var line:samples){if(sampleY+10>box.bottom()-54)break;g.text(font,line,x,sampleY,BLUE,false);sampleY+=12;}
        }
        if(box.height()>=250)fit(g,"Изображения можно скачать после добавления.",x,box.bottom()-52,w,MUTED);
    }
    private void openCatalogDiagnostics(){
        releaseNotesOpen=false;eventsOpen=false;catalogErrorReport=true;reportOpen=true;reportScroll.row=0;
        report=MoonotesNetworkClient.catalogErrorDetails();
        if(report.isBlank())report="Ошибок получения каталога нет.";
        report+="\n\nСохранённые каталоги и изображения остаются доступны без сети.\nПовторите запрос после восстановления соединения. При недоступном 7TV нужен обновлённый Worker 0.5.3.";
        reportLines.clear();for(String line:report.split("\\R"))reportLines.add(line);
        clearFocus();rebuildMoonotesWidgets();
    }

    private void openCatalogForm(int mode,String target){
        catalogDetailKey="";catalogPickerOpen=false;catalogFormOpen=true;
        formMode=mode;formValue="";importTarget=target;noticeUntil=0;importGeneration++;fileImportBusy=false;rebuildMoonotesWidgets();
    }
    private void buildCatalogForm(){
        var box=modalBox();
        int x=box.x()+12,w=box.width()-24;
        String[] modes={"Twitch-канал","Набор 7TV","JSON"};
        int optionWidth=(w-12)/3;
        for(int i=0;i<3;i++){
            final int choice=i;
            var button=button(rect(x+i*(optionWidth+6),box.y()+34,optionWidth,22),modes[i],
                i==0?"Логин Twitch без @ и ссылки. Будет найден набор 7TV этого канала.":i==1?"ID набора 7TV: последняя часть адреса страницы набора.":"Вставьте JSON каталога либо перетащите .json на это окно.",formMode==i,b->{formMode=choice;formValue="";rebuildMoonotesWidgets();});
            button.active=importTarget.isEmpty() || i==2;
        }
        if(formMode==2){
            jsonField=addRenderableWidget(MultiLineEditBox.builder().setX(x).setY(box.y()+79)
                .setPlaceholder(Component.literal("{\"name\":\"Мой набор\",\"emotes\":[{\"id\":\"…\",\"name\":\"…\"}]}"))
                .build(font,w,Math.max(26,box.height()-145),Component.literal("JSON каталога")));
            jsonField.setCharacterLimit(CatalogStore.MAX_JSON);
            jsonField.setValue(formValue);
            jsonField.setValueListener(v->formValue=v);
            setFocused(jsonField);
        }else{
            catalogField=addRenderableWidget(new MoonotesEditBox(font,x,box.y()+79,w,22,Component.literal(modes[formMode])));
            catalogField.setMaxLength(200);
            catalogField.setHint(Component.literal(formMode==0?"Например: v0kky":"ID набора 7TV"));
            catalogField.setValue(formValue);catalogField.setResponder(v->formValue=v);setFocused(catalogField);
        }
        int half=(w-6)/2;
        submitCatalogButton=button(rect(x,box.bottom()-32,half,22),importTarget.isEmpty()?(formMode==2?"Добавить":"Найти каталог"):"Обновить JSON",
            "Проверить формат и сохранить каталог локально. Изображения загружаются отдельно.",true,b->{
                if(formMode==0 || formMode==1){
                    String kind=formMode==0?"channel":"set",value=formValue;
                    closeOverlay();tab=1;MoonotesNetworkClient.beginCatalogPreview(kind,value);rebuildMoonotesWidgets();return;
                }
                if(importTarget.isEmpty())MoonotesNetworkClient.importCatalogJson(formValue);
                else MoonotesNetworkClient.updateImportedCatalog(importTarget,formValue);
                closeOverlay();tab=1;rebuildMoonotesWidgets();
            });
        submitCatalogButton.active=!formValue.isBlank() && !MoonotesNetworkClient.catalogBusy();
        button(rect(x+half+6,box.bottom()-32,half,22),"Назад","Закрыть форму без добавления каталога.",false,b->closeOverlay());
    }
    private void drawCatalogForm(GuiGraphicsExtractor g,MoonotesLayout.Rect box){
        fit(g,formMode==2?"Вставьте JSON или перетащите файл .json (до 1 МиБ)":formMode==0?"Канал Twitch → его набор 7TV":"Набор 7TV → список названий и изображений",
            box.x()+12,box.y()+64,box.width()-24,MUTED);
        String hint=System.currentTimeMillis()<noticeUntil?statusNotice:fileImportBusy?"Читаем JSON…":MoonotesNetworkClient.catalogBusy()?"Получаем каталог…":formMode==2?"Ссылки на сторонние изображения не исполняются":"Новый каталог будет сохранён для работы без сети";
        fit(g,hint,box.x()+12,box.bottom()-51,box.width()-24,MUTED);
    }
    @Override public void onFilesDrop(List<java.nio.file.Path> files){
        if(!catalogFormOpen || formMode!=2 || files.size()!=1){
            statusNotice="Для импорта откройте «Каталоги → Добавить → JSON».";noticeUntil=System.currentTimeMillis()+5000;return;
        }
        var path=files.getFirst();
        if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")){
            statusNotice="Нужен файл с расширением .json";noticeUntil=System.currentTimeMillis()+5000;return;
        }
        if(fileImportBusy)return;
        String target=importTarget;
        long generation=importGeneration;
        fileImportBusy=true;noticeUntil=0;
        Thread.ofPlatform().daemon().name("Moonotes-JSON-Import").start(()->{
            try{
                String json=new String(CacheIntegrity.boundedRead(path,CatalogStore.MAX_JSON),java.nio.charset.StandardCharsets.UTF_8);
                minecraft.execute(()->{
                    if(generation!=importGeneration || !catalogFormOpen || minecraft.gui.screen()!=this)return;
                    fileImportBusy=false;
                    if(target.isEmpty())MoonotesNetworkClient.importCatalogJson(json);else MoonotesNetworkClient.updateImportedCatalog(target,json);
                    closeOverlay();tab=1;rebuildMoonotesWidgets();
                });
            }catch(Exception ex){minecraft.execute(()->{
                if(generation!=importGeneration || !catalogFormOpen || minecraft.gui.screen()!=this)return;
                fileImportBusy=false;statusNotice="Не удалось импортировать JSON: проверьте размер и формат.";noticeUntil=System.currentTimeMillis()+6000;
            });}
        });
    }
    private MoonotesNetworkClient.CatalogView detailCatalog(){
        return MoonotesNetworkClient.catalogs().stream().filter(c->c.key().equals(catalogDetailKey)).findFirst().orElse(null);
    }
    private void buildCatalogDetail(){
        var c=detailCatalog();if(c==null){catalogDetailKey="";rebuildMoonotesWidgets();return;}
        var box=modalBox();int x=box.x()+12,w=(box.width()-30)/2;
        if(removeConfirm){
            button(rect(x,box.bottom()-32,w,22),"Удалить каталог","Убрать только каталог. Общие PNG и метаданные анимации останутся на диске.",true,b->{MoonotesNetworkClient.removeCatalog(c.key());closeOverlay();});
            button(rect(x+w+6,box.bottom()-32,w,22),"Отмена","Сохранить каталог.",false,b->{removeConfirm=false;rebuildMoonotesWidgets();});
            return;
        }
        button(rect(x,box.bottom()-88,w,22),c.enabled()?"В библиотеке: вкл":"В библиотеке: выкл",
            "Скрыть или показать каталог в библиотеке. Для наборов и JSON это также управляет регистрацией эмотов.",false,b->{MoonotesNetworkClient.toggleCatalog(c.key());rebuildMoonotesWidgets();});
        String channel=c.key().startsWith("channel:")?c.key().substring(8):"";
        var chat=button(rect(x+w+6,box.bottom()-88,w,22),channel.isEmpty()?"Чат: как библиотека":StreamotesBridge.channelEnabledForCurrentWorld(channel)?"В чате мира: вкл":"В чате мира: выкл",
            "Включить канал для чата текущего мира или сервера. Каталог также должен быть включён в библиотеке.",false,b->{
                StreamotesBridge.setChannelEnabledForCurrentWorld(channel,!StreamotesBridge.channelEnabledForCurrentWorld(channel));
                rebuildMoonotesWidgets();
            });
        chat.active=!channel.isEmpty() && minecraft.player!=null;
        button(rect(x,box.bottom()-60,w,22),c.kind().equals("import")?"Заменить JSON":"Обновить список",
            "Сравнить новый список с сохранённым. Изменения названий будут учтены, старые изображения сохранятся.",false,b->{
                if(c.kind().equals("import"))openCatalogForm(2,c.key());else{MoonotesNetworkClient.syncCatalog(c.key());rebuildMoonotesWidgets();}
            }).active=!MoonotesNetworkClient.catalogBusy();
        button(rect(x+w+6,box.bottom()-60,w,22),"Убрать каталог","Удаление списка с подтверждением. Общий кеш изображений остаётся на месте.",false,b->{removeConfirm=true;rebuildMoonotesWidgets();});
        button(rect(x,box.bottom()-32,w,22),"Открыть эмоты","Выбрать этот каталог и перейти в библиотеку.",true,b->{MoonotesNetworkClient.selectCatalog(c.key());closeOverlay();tab=0;rebuildMoonotesWidgets();});
        button(rect(x+w+6,box.bottom()-32,w,22),"Назад","Вернуться к списку каталогов.",false,b->closeOverlay());
    }
    private void drawCatalogDetail(GuiGraphicsExtractor g,MoonotesLayout.Rect box){
        var c=detailCatalog();if(c==null)return;
        fit(g,c.name(),box.x()+12,box.y()+35,box.width()-24,BLUE);
        if(removeConfirm){
            fit(g,"Убрать каталог из Moonotes?",box.x()+12,box.y()+57,box.width()-24,TEXT);
            fit(g,"Общие файлы Streamotes сохранятся.",box.x()+12,box.y()+76,box.width()-24,GREEN);
            fit(g,"Каталог можно добавить снова.",box.x()+12,box.y()+95,box.width()-24,MUTED);return;
        }
        String[] lines={"В кеше "+c.cached()+"/"+c.total(),"При обновлении: добавлено "+c.added()+", убрано "+c.removed(),
            "Источник: "+c.status(),"Обновлён: "+(c.lastSync()==0?"встроенная копия":java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(c.lastSync()))),
            "Файлы кеша общие с Streamotes. Удаление списка их не затронет."};
        int y=box.y()+53;
        for(String line:lines){if(y+9>box.bottom()-97)break;fit(g,line,box.x()+12,y,box.width()-24,MUTED);y+=16;}
    }
    private void configurePicker(){
        var box=modalBox();int rows=Math.max(1,(box.height()-81)/26);
        pickerScroll.configure(box.y()+34,rows*26-4,rows,Math.max(0,pickerCount()-rows));
    }
    private int pickerCount(){return 1+(int)MoonotesNetworkClient.catalogs().stream().filter(MoonotesNetworkClient.CatalogView::enabled).count();}
    private MoonotesLayout.Rect pickerTrack(){var box=modalBox();return rect(box.right()-18,box.y()+34,13,Math.max(1,(box.height()-81)/26)*26-4);}
    private void buildCatalogPicker(){
        configurePicker();var box=modalBox();
        List<String> keys=new ArrayList<>(),names=new ArrayList<>();keys.add("all");names.add("Все включённые каталоги");
        for(var c:MoonotesNetworkClient.catalogs())if(c.enabled()){keys.add(c.key());names.add(c.name());}
        int rows=Math.max(1,(box.height()-81)/26);
        for(int row=0;row<rows;row++){
            int index=pickerScroll.row+row;if(index>=keys.size())break;
            String key=keys.get(index);
            button(rect(box.x()+12,box.y()+34+row*26,box.width()-39,22),names.get(index),"Показывать эмоты выбранного каталога.",key.equals(MoonotesNetworkClient.selectedCatalog()),b->{MoonotesNetworkClient.selectCatalog(key);libraryScroll.row=0;filterDirty=true;closeOverlay();});
        }
        button(rect(box.x()+12,box.bottom()-32,box.width()-24,22),"Назад","Оставить текущий выбор.",false,b->closeOverlay());
    }
    private void buildCacheModal(){
        var box=modalBox();int x=box.x()+12,w=(box.width()-30)/2;
        repairButton=button(rect(x,box.bottom()-60,w,22),CacheMaintenance.busy()?"Остановить проверку":"Проверить и исправить",
            "Проверить PNG, кадры анимации и регистрацию, затем скачать отсутствующие и повреждённые файлы выбранного каталога. Также проверяются 7TV и Cloudflare (до 6 МиБ трафика). Исправные файлы сохраняются.",true,b->{
                if(CacheMaintenance.busy())CacheMaintenance.cancel();else CacheMaintenance.start(true);
            });
        repairButton.active=!MoonotesNetworkClient.isDownloading();
        button(rect(x+w+6,box.bottom()-60,w,22),"Найти отсутствующие","Открыть библиотеку только с эмотами, которых нет в кеше. В том числе после неудачного восстановления.",false,b->{closeOverlay();tab=0;sortMode=0;filterMode=2;filter="";filterDirty=true;libraryScroll.row=0;rebuildMoonotesWidgets();});
        button(rect(x,box.bottom()-32,w,22),"Обновить регистрацию","Повторно зарегистрировать названия из локальных каталогов. Скачивание не запускается.",false,b->{StreamotesBridge.refresh();statusNotice="Регистрация обновляется";noticeUntil=System.currentTimeMillis()+3000;});
        button(rect(x+w+6,box.bottom()-32,w,22),"Назад","Закрыть это окно. Проверка продолжится в фоне.",false,b->closeOverlay());
    }
    private void drawCacheModal(GuiGraphicsExtractor g,MoonotesLayout.Rect box){
        var health=StreamotesBridge.health();
        String[] lines={"Каталог: "+MoonotesNetworkClient.selectedCatalogLabel(),
            "В кеше: "+MoonotesNetworkClient.cachedImageCount()+"/"+MoonotesNetworkClient.collectionImageCount()+" · "+size(MoonotesNetworkClient.channelCacheSize()),
            "Общий кеш на диске: "+size(MoonotesNetworkClient.cacheSize()),
            "Имена для чата: "+health.registered()+"/"+health.expected()+" · Конфликтов: "+health.conflicts(),
            health.problem().isBlank()?StreamotesBridge.activationLabel():health.problem(),
            "Имена для чата — это реестр, а не число готовых изображений.",
            CacheMaintenance.status(),"Проверен: "+CacheMaintenance.probeTarget(),
            "7TV: "+CacheMaintenance.direct(),"Cloudflare: "+CacheMaintenance.alternative(),
            "Не хватает: "+MoonotesNetworkClient.firstMissingSummary(),
            "Удаление каталога сохраняет общие файлы изображений."};
        if(box.height()<280)lines=new String[]{
            "Кеш "+MoonotesNetworkClient.cachedImageCount()+"/"+MoonotesNetworkClient.collectionImageCount()+" · "+size(MoonotesNetworkClient.channelCacheSize())+" / "+size(MoonotesNetworkClient.cacheSize()),
            "Имена в чате: "+health.registered()+"/"+health.expected()+" · Конфликтов "+health.conflicts(),
            health.problem().isBlank()?StreamotesBridge.activationLabel():health.problem(),
            "Имена для чата — это реестр, а не число готовых изображений.",
            CacheMaintenance.status(),"Не хватает: "+MoonotesNetworkClient.firstMissingSummary(),
            "Проверен: "+CacheMaintenance.probeTarget(),
            "7TV: "+CacheMaintenance.direct(),"Cloudflare: "+CacheMaintenance.alternative()};
        int y=box.y()+34;
        for(int i=0;i<lines.length;i++) {
            if(y+9>box.bottom()-68)break;
            fit(g,lines[i],box.x()+12,y,box.width()-24,i==3?(health.good()?GREEN:WARNING):i==5?BLUE:MUTED);y+=16;
        }
    }

    @Override public void removed(){
        importGeneration++;fileImportBusy=false;
        libraryScroll.release();settingsScroll.release();reportScroll.release();catalogScroll.release();pickerScroll.release();if(previews!=null){previews.close();previews=null;}super.removed();
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fillGradient(0,0,width,height,0xB808101C,0xDF070D16);
    }
    @Override public boolean isInGameUi(){return true;}
    @Override public boolean isPauseScreen(){return false;}
}

