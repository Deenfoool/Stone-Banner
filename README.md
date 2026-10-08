# Stone & Banner

Stone & Banner is a work-in-progress total-conversion mod for Minecraft Java Edition 1.20.1 on Forge. It is designed around a third-person action-RPG camera, selectable WASD or mouse movement, real-time combat, settlement management, and terrain-driven resources.

The product direction and development stages are documented in [ROADMAP.md](ROADMAP.md).

## Development requirements

- 64-bit Java 17
- Minecraft 1.20.1
- Forge 47.4.23

## Useful commands

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat verifyGameTests
```

## Current prototype

**Citizen Orders (alpha.48, source implementation awaiting verification):** In Orders mode press `Ctrl+1–9` to save a group, `Alt+1–9` to recall it, or double-tap `Alt+number` to focus the camera on the group's loaded members. `Alt+RMB` queues movement waypoints or follow/attack targets; `Alt+Shift+RMB` on a log/ore queues an already existing work job. The server executes one at a time with UUID-checked targets, timeouts and safe interruption; Stop or a new direct order clears pending work. HUD displays the number of waiting orders. Alt+Shift+RMB on buttons, levers, doors, trapdoors and fence gates queues a safe one-shot Interact command; arbitrary inventories/GUI or modded blocks are excluded. The selected citizen shows active/queued orders in the HUD and yellow/blue world markers. Protocol **25** requires matching client/server. See [CONTROLS.md](CONTROLS.md).

The Phase 1.7 Hero ↔ Orders transition is implemented in source: tap `Tab` to toggle Orders, or hold it from Hero to give temporary orders and return automatically on release. `Home` returns the camera to the hero; double-pressing it resets the default angle and zoom. Detached Orders camera pans at screen edges and keeps independent yaw/zoom while the hero's previous camera view is restored on return. Controls are rebindable; `ordersHoldMs`, `homeDoubleMs` and `transitionTicks` live in the client TOML. Camera interpolation still uses collision and loaded-chunk checks. See [CONTROLS.md](CONTROLS.md). These changes have not been verified in a Forge client or Gradle build yet.

Version: `0.1.0-alpha.48` (Phase 1.11 settings source implementation; Forge build/runtime acceptance pending).

Cottage construction is implemented in source: **B** / the Build tab opens a fixed 5×7 blueprint with rotation and a world outline preview. One NPC fetches real supplies from registered storage, builds in stages, and places a door/two beds with one item per furniture piece. Plans, ownership and pause persist; removing a plan keeps blocks and carried items. Existing sleep/camp provision detects the ordinary beds. See [CONSTRUCTION.md](CONSTRUCTION.md). Build and gameplay verification remain deferred.

NPC storage now works without operator commands: Shift + right-click a chest/barrel with an empty main hand, then choose Use as storage. Double chests connect both halves; disconnecting preserves all contents. Registration managers persist, foreign/legacy registrations remain protected, and existing NPC supply/delivery and camp readiness use the same registry. Normal container interaction is preserved. See [STORAGE.md](STORAGE.md).

Production orders can now change quantity/mode without losing completed output, and move up/down within their workstation queue. The order and settings persist across saves; an already started operation finishes before the next order is selected. Changes since alpha.34 await build and gameplay verification.

Fields and production are available with **K** or `/sbproduction menu`: four crops with physical hoe/seed fetching, tilling, optional bone meal, mature harvest, cargo delivery and replanting; persistent MAKE/MAINTAIN bills with actual shaped/shapeless 3×3 recipes, ingredients and recipe remainders. New carpenter/forge workbenches also support manual crafting. Orders wait for materials/output storage and honor NPC priorities/ownership. See [PRODUCTION.md](PRODUCTION.md) for setup and limitations. This does not yet include furnace/stonecutter/brewing/loom automation or animal farming. Current client/server protocol: **25**.

NPCs search for reachable free beds in loaded chunks within 32 blocks, reserve a bed while walking, and use Minecraft's actual sleeping pose/occupied state on arrival. Walking restores neither fatigue nor treatment progress. Wake-up, hunger, danger, manual orders, damage, removal and destroyed beds release the place; NBT reload revalidates it instead of keeping a phantom reservation. Without a reachable bed, ground rest remains available. Overview distinguishes approach, bed sleep and ground rest. See [SLEEP.md](SLEEP.md).

Roadmap block 5.5 now has playable localized injuries and first aid: actual HP loss creates body trauma after vanilla/Forge damage processing; untreated severe head/torso injuries bleed and interrupt jobs. Craft bandages (paper + string, two) and splints (two sticks + string, one), then right-click a nearby NPC or use the Health tab buttons. One real supply treats the worst eligible injury; recovery requires rest and hunger below 60, changes body states in stages and persists across saves. Ownership, UUID/world, range and line of sight are checked server-side. See [MEDICINE.md](MEDICINE.md). Automatic doctors, patient transport, hospitals and limb loss remain separate future work.

NPC inspector overhaul: all five tabs share world/UUID identity and server snapshots, refreshing once per second. Long summaries, body/skill lists, all eleven work columns and nearby NPC rows are paged. Health shows actual body movement/work/combat efficiency; Skills shows persistent XP, including Forestry; gold inventory slots indicate real work cargo. Foreign recruited NPCs cannot expose private inventory data or accept priority edits; returning NPCs remain inspectable by their owner but read-only. Work tooltips identify implemented jobs versus planned priorities. See [NPC_LOGIC_AUDIT.md](NPC_LOGIC_AUDIT.md).

AI practice now follows successful block work (5 XP), ladder placement (5 XP), farm/crafting operations (5 XP), hits (2 XP) and completed geology surveys (25 XP), with persistent level thresholds and cap 10. Skills affect implemented work and combat; injuries scale actual attack damage without replacing vanilla damage hooks. Fatigue rest continues until fatigue reaches 25; medical rest continues while treatment is active or core injuries remain dangerous. Neither can be hijacked by cargo delivery/home return. Accessible beds and farming/crafting cycles are implemented; automatic doctors are not.

The village/quest journal is available through `/sbvillage journal` or the "Open journal" chat link at a board/resident. It uses Minecraft fonts and item icons, pages registered villages and quests, and shows population, trust, elder, recipient coordinates, actual supply/kill progress and reward. Accept/submit buttons still require the server's normal distance, visibility and item checks; use Refresh for a new snapshot. Client and server must use matching mod versions.

Recruitment UI: use the journal's Recruit button, the village chat link, `/sbvillage recruitment`, or `/sbvillage manage` for your contracts. Shift+right-click a recruited NPC also opens their village's contract tab. The window shows professions, prices, status and coordinates; hover an action button for eligibility/refusal details. Hiring and dismissal require confirmation. The server rechecks conditions and the confirmed price, without trusting client eligibility; stale-price attempts do not charge emeralds. Server-side pages contain four residents or own contracts, never foreign contracts.

Village contracts now support dismissal and physical return to the original village. Use `/sbvillage contracts` or Shift+right-click your recruited NPC, then select the dismissal link near the NPC. See [VILLAGES.md](VILLAGES.md) for return rules and legacy-contract limitations.

- Village loop: a loaded bell with three adult villagers creates a persistent independent community and notice board. An elder is elected and replaced on death; local trust, authored supply/defence quests and contracts survive saves. Right-click the board/elder, or Shift+right-click another resident; open chat with T to use translated action links. Vanilla villagers retain their normal AI/trades until explicitly recruited into a Human NPC.
- Companions require trust 20; settlers require 40 plus an active provisioned camp and a reachable loaded route within 64 blocks. Contracts use real emeralds; the elder, last professional specialist and villages with only three residents are protected. Recruits transfer their personal inventory, have persistent player authority and settlers walk to their new home. Dismissal supports physical return for new contracts; recurring wages and named overhaul adapters are still pending. See [VILLAGES.md](VILLAGES.md).

- Hungry citizens eat personal food first, then walk to reachable registered food storage within 64 blocks. One real portion is extracted on arrival; bowls/bottles are retained or physically dropped if the personal bag is full. No remote consumption or virtual food stock. Ordinary hunger respects manual movement/work; critical hunger overrides orders. Beds are implemented; advance portion reservations are not implemented yet.

- NPC lifecycle hardening: reservation ownership and disabled-work checks, bounded follow retries, failed-route recovery, UUID-bound targets, active guard defense, immediate death cleanup and physical inventory/cargo death drops. See [NPC_LOGIC_AUDIT.md](NPC_LOGIC_AUDIT.md) for coverage and remaining gaps.

- Forge project foundation and dedicated client configuration.
- Dedicated-server packet registration no longer resolves GUI classes. Server movement-queue integration tests run with `verifyGameTests` in the isolated `run-gametest` world; test classes and generated structure fixtures are excluded from the mod JAR and ordinary runs.
- Rebindable `F9` read-only diagnostics for camera, hero path, selected NPC and excavation snapshots; hidden with F1 and reset on disconnect.
- Two hero movement profiles (WASD / mouse) and a separate group-order mode (Tab).
- Hero mouse profile: `Alt + LMB` appends ground movement waypoints (16 pending). A normal click, RMB action, control-mode change, death or failed route cancels the queue. Menus suspend movement; direct free-swimming, combat and interactions are not queued. Pending count is shown in diagnostics. No additional server packets are used for hero waypoints.
- In orders mode, `Alt + RMB` on a block appends a movement waypoint for selected citizens (up to 16 pending points per NPC). An idle NPC starts immediately; normal movement replaces the queue, Stop clears it, and failure/critical preemption cancels pending points. Queues are transient, not saved. Follow, attack and work orders do not yet support queuing. Protocol 17 requires matching client/server mod versions.
- In orders mode, `Ctrl+1–9` saves the selection to a group, `Alt+1–9` recalls it and `Shift+Alt+1–9` adds it. Save an empty selection to clear a group. Groups use NPC UUIDs, retain temporarily unloaded members and reset on disconnect/world or dimension change; they are not saved to disk. All modes keep normal hotbar number keys; group bindings can be reassigned.
- In-game mode cycling with `V` (rebindable in Minecraft controls).
- Third-person perspective enforcement.
- Smooth mouse-wheel camera zoom from 2 to 24 blocks.
- Configurable camera height and smoothing.
- Independent rotation, pan and zoom sensitivity, optional inverted pitch and switchable edge scrolling.
- Camera collision checks swept volumes and the near plane against visual block shapes.
- Independent camera yaw and pitch while holding the middle mouse button.
- WASD movement is transformed relative to the camera direction.
- Both hero profiles use a free world cursor without pausing the game.
- Tactical raycasts select blocks under the real cursor instead of the central crosshair.
- Mouse profile: left click moves/interacts; held right click performs the equipped-item action.
- The tactical overlay shows the hovered block and current movement state.
- Tactical movement uses a bounded A* route over walkable blocks.
- Routes can climb and descend one block, avoid basic hazards, and rebuild when the player is stuck.
- Route nodes and the final destination are rendered directly in the world.
- The tactical cursor resolves entities in front of blocks and shows their names.
- Hovered and selected entities receive different world-space outlines.
- Held right click attacks creatures with vanilla cooldown; release stops repeated attacks.
- Left click on an NPC approaches and interacts. Group selection is separate from hero actions.
- Navigation uses collision-surface heights for bottom slabs and stairs.
- Water nodes support swimming up and down, with a higher route cost than dry ground.
- Closed wooden doors can be planned through and are opened on approach.
- Routes can climb and descend ladders, including vertical input while attached.
- Open trapdoors and iron doors are recognized as passages; closed bottom trapdoors are walkable surfaces.
- Waterlogged solid blocks no longer become false swimming routes.
- World-cursor projection uses the camera's updated orientation vectors and rendered FOV.
- A small world-space marker shows the exact ray impact point before clicking.
- Camera settings persist in `config/stonebanner-client.toml`.
- Exposed ore discoveries have Allow mining, Show vein, Go to vein and Dismiss actions.
- Press `N` (rebindable) to open the ore discovery journal, including dismissed findings.
- Mining permission covers surveyed blocks within an existing excavation zone. New exposed blocks require renewed permission; outside-zone ore requires extending the designation.
- Camera focus moves to a loaded vein without teleporting the hero; use Return to hero in the journal to restore the camera.
- Select a citizen in orders mode (Tab), then press `F10` to smoothly follow that NPC with the camera. `Home` returns to the hero; both keys are rebindable. Camera panning cancels follow, while rotation and zoom keep it. Death, unloading, world changes or moving farther than 64 blocks from the hero reset follow. This does not transfer control or teleport the hero; focus remains on the original NPC until changed explicitly.
- Excavation safety queries require an exact route through the access structure to an exterior cell, avoiding the block being mined and ladder attachments that would be removed. Automated tests and the build pass; in-game verification is pending. See the roadmap.
- Nearby block changes invalidate only affected excavation plans; their current slices are repaired at the end of the server tick while the periodic full reconciliation remains a fallback.

### Camera preferences

Edit the `[camera]` section in `config/stonebanner-client.toml` with the game closed, then restart it:

```toml
rotationSensitivity = 1.0
panSensitivity = 1.0
zoomSensitivity = 1.0
invertVertical = false
edgePan = true
```

Sensitivity multipliers range from `0.1` to `4.0`; `1.0` preserves the original controls.
`edgePan = false` disables cursor-edge scrolling without disabling WASD/arrow camera movement
or Shift + middle-button dragging in group-order mode. Vertical inversion affects only camera rotation,
not hero aiming. These preferences do not yet have an in-game settings screen.

The pathfinder now has prototype handling for stairs, bottom slabs, water, wooden doors, ladders,
trapdoors and open iron doors. Closed iron access still requires a player-operated redstone mechanism.
Combat delegates held hero attacks and commanded NPC melee attacks to Minecraft; the custom real-time combat
system and settlement simulation are later roadmap stages.

## Основание лагеря через баннер

Любой напольный ванильный баннер остаётся декором при установке. Нажмите ПКМ в режиме героя, выберите «Основать лагерь», введите имя и подтвердите. Герой сначала подходит к баннеру. При отключённом новом управлении сохраняется ванильный вход ПКМ пустой рукой и обход меню через Shift. Цвет и узор не влияют на основание.

Один лагерь на игрока в измерении получает территорию 3×3 чанка, без пересечения с другими общинами и рядом с деревнями. Разрушение флага сохраняет общину и жителей. Поставьте другой баннер внутри прежних границ и подтвердите назначение центром: территория и дома не перемещаются.

Для прототипа поселения нужны минимум один принятый NPC с ролью `SETTLER`, кровать каждому жителю и игроку, зарегистрированный склад и по четыре съедобных предмета на человека. Выберите переселенца в режиме приказов и примите через меню знамени. Учитываются только загруженные кровати, реальные предметы и сущности; зарегистрированные жители вне загруженных чанков тоже включены в требуемые запасы. Полная вербовка через репутацию ещё не реализована.

В текущем прототипе склад регистрируется операторской командой `/stonebanner storage add X Y Z`; роль переселенца задаётся командой `/stonebanner npc participation <npc> settler`. Игровые найм и регистрация склада через интерфейс остаются в роадмапе.

## Слои территории и геология

В режиме приказов три кнопки под скоростью времени включают границы общин, ресурсы чанков и плодородные земли. Горячие клавиши во всех режимах: **F6 / F7 / F8** (переназначаются). Границы можно совмещать с любым слоем; ресурсы и плодородие переключаются между собой. Ресурсный слой окрашивает поверхность, показывает подписи внутри ближайших чанков и сведения о чанке под курсором.

Исследовательский стол: крафт `книга — железный слиток — книга`, затем два ряда досок. Поставьте его в своей территории и откройте ПКМ в режиме героя либо ПКМ в режиме приказов. Исследования общие для лагеря/поселения, оплачиваются реальными предметами из инвентаря основателя и требуют работы рядом со столом (8 блоков). Прогресс сохраняется; при уходе, выходе из игры или потере стола он приостанавливается. Другой стол внутри общины позволяет продолжить без повторной оплаты.

| Этап | Сведения после обследования | Бумага / книги / дополнительно | Работа |
|---|---|---|---|
| Старт | Только наличие признаков залежей | — | — |
| Основы геологии | Общая оценка богатства | 8 / 1 | 30 сек. |
| Поиск обычных руд | Уголь, медь, железо и кварц с оценкой | 16 / 1 | 60 сек. |
| Глубинная геология | Дополнительно наличие ценных руд, без их богатства | 24 / 1 / 2 золота | 90 сек. |
| Полное обследование | Богатство каждого вида руды | 32 / 1 / 1 алмаз | 120 сек. |

Примите жителя через знамя, выберите его в режиме приказов и назначьте геологом через меню стола. Для обследования включите ресурсы, выберите геолога и нажмите **Shift + ПКМ по земле в режиме приказов** нужного чанка. Он физически идёт к точке, работает 14–24 секунды в зависимости от навыка, получает опыт и добавляет сведения общине. Более высокий метод требует повторного обследования. Новая команда, опасность, критические потребности, выгрузка NPC или истечение времени прерывают незавершённое обследование без раскрытия данных. Завершённые обследования сохраняются вместе с миром.

Сервер считает реальные блоки руды, группируя обычную и глубинную разновидности. Для каждого вида свои пороги скудных/умеренных/богатых залежей. Скрытые названия, точные количества и подземные координаты не отправляются клиенту. Подсчёт ограничен 8192 блоками на измерение за тик, не загружает новые чанки; кэш обновляется после добычи игроком/NPC и периодически для сторонних изменений. При первой оценке возможна короткая задержка.

Плодородие сейчас — индекс пригодности реальной поверхности по почве, климату, влажности грядок и воде. Камень и водоёмы не подсвечиваются как плодородные. Это ещё не изменение скорости роста ванильных культур; истощение почвы и удобрения остаются в роадмапе.

## Камера под крышей и в шахтах

Камера проверяет весь путь от глаз игрока к приподнятой точке и затем назад к выбранному масштабу. Потолки, стены, плиты, лестничные блоки, двери и другие визуальные формы ограничивают движение с запасом для ближней плоскости камеры. В тесном месте фактическая дистанция может стать меньше минимального пользовательского зума; выбранные высота и масштаб сохраняются и восстанавливаются после выхода на открытое место. Коррекция столкновения применяется сразу, без задержки сглаживания. Переход к жиле тоже не проходит через стены; незагруженные чанки считаются препятствием.

Если сами глаза игрока оказались внутри геометрии и безопасной исходной точки нет, обзор мира временно закрывается, включая режим F1. Интерфейс остаётся доступным, а тактический курсор не выбирает объекты через препятствие. Это резервная защита для застревания/телепортации внутри блоков.

## Управление героем и приказы — alpha.15

**V** переключает два профиля ходьбы: **WASD** (направления относительно экрана) и **мышь** (ЛКМ по земле; удержание обновляет маршрут вслед за курсором). Старые настройки TACTICAL автоматически интерпретируются как профиль мыши; ACTION и HYBRID сохраняют совместимость конфигурации.

Остальные действия одинаковые:

- **ПКМ по интерактивному блоку или NPC** — герой подходит и использует объект. Human NPC открывает существующую карточку; обычный житель — ванильную торговлю. Сундуки, верстаки, двери, рычаги, знамя и исследовательский стол используют реальные игровые механики.
- **WASD: удержание ЛКМ — удар/добыча, ПКМ — использование. Mouse: ЛКМ — только движение, удержание ПКМ по ресурсу/противнику — добыча/удар.** Меч/оружие атакует существо с ванильной перезарядкой; кирка, топор, лопата или пустая рука добывают блок. В Mouse герой подходит; в WASD удар и добыча требуют реальной дальности. Отпускание прекращает добычу и повторение атак.
- **Лук: держать ПКМ — натягивать и целиться, отпустить — выстрелить.** Прицеливание учитывает высоту существа; используется настоящий лук, стрелы и серверная физика Minecraft. WASD позволяет двигаться одновременно со стрельбой.
- **Блок в руке + ПКМ** — установка; бирюзовый контур показывает предполагаемую ячейку, окончательное разрешение и форму определяет Minecraft. Еда и другие используемые предметы проходят обычное использование.
- **Space** — прыжок / всплытие; **Shift** — присесть / погружение; **Ctrl** — бег. В профиле мыши курсор над водой выбирает поверхность, а в воде задаёт горизонтальное направление плавания вместо маршрута по дну. Space/Shift доступны в обоих профилях. Перекат не вводится.
- **1–9** выбирают слот; колесо над панелью предметов переключает слоты, над миром — зум. **E** открывает инвентарь. Средняя кнопка с движением вращает камеру.

**Tab** переключает режим приказов, сохраняя профиль ходьбы. В нём ЛКМ выбирает жителя, протягивание ЛКМ выделяет рамкой только видимых загруженных Human NPC, Shift добавляет к группе (Shift + одиночный клик также убирает). Максимум 64 жителя; счётчик и контуры показывают выбор. ПКМ по земле отправляет группу идти, по враждебному существу — преследовать/атаковать, по дружественному — следовать. ПКМ по дереву/руде предлагает работу выбранным жителям через настоящую систему заданий, навыков, потребностей и резервирования; один блок не резервируется несколькими работниками. Обнаруженная руда сохраняет требование разрешения на добычу. Для зон работ остаётся существующая панель обозначений.

Камера в режиме приказов свободная: курсор у краёв экрана перемещает её, углы дают диагональ с той же скоростью; над панелями, во время рамки и вне активного окна прокрутка отключена. Дополнительно работают переназначаемые клавиши движения, Page Up/Down и Shift + средняя кнопка. **Home** возвращает обзор к герою; **Space в режиме приказов** останавливает выбранных жителей и героя. При выходе через Tab камера снова следует за героем. Сохраняются ограничения стен, потолков, незагруженных чанков и радиус камеры 64 блока.

Это новая реализация, а не проверенное воспроизведение PoE 2. Автоматическая сборка и тесты не заменяют ручную проверку игрового клиента: плавание, добыча, лук, меню, групповая рамка, бой NPC и управление камерой требуют игровых сценариев.

## Логистика добычи — alpha.16

Добыча остаётся настоящими предметами: рабочий с разрешённой переноской подбирает дроп в отмеченные грузовые слоты своего сохраняемого инвентаря, остаток создаёт задания для транспортировщиков. Выключите шахтёру работу «Переноска», чтобы её выполняли отдельные NPC. Уже взятый груз доставляется даже после отключения этой работы и не исчезает при сохранении/загрузке.

Доставка рассматривает загруженные зарегистрированные склады в радиусе 64 блоков, которые принимают хотя бы один тип груза. Проверяется проходимый подход и видимость контейнера; недоступный ближайший склад не закрывает поиск следующих. За одну попытку рассматриваются до восьми складов и по три точки подхода; суммарный бюджет — 24 поиска маршрута на NPC за тик с повторным использованием результатов внутри тика. После неудачного движения тот же склад временно пропускается, а ожидание повторяет поиск раз в две игровые секунды. Чанки принудительно не загружаются.

Частичная выгрузка оставляет остаток у NPC. Заполненный, удалённый или недоступный склад не превращает груз в глобальный счётчик и не удаляет предметы. Подбор заданий переносчика учитывает разные типы дропа, а не только первую стопку. Вставка уважает запрет предметов в слоты, в том числе при дополнении существующей стопки.

Карточка и обзор жителя показывают количество груза и статус: доставка, выгрузка, нет места/подходящего склада, нет доступного пути, потребности/опасность или прямой приказ игрока. Ожидающая доставка уступает командам движения/следования/атаки и возобновляется после их завершения. Ручная проверка маршрутов и интерфейса в игре ещё требуется.

## Безопасность и причины остановки работ — alpha.17

Перед началом добычи, рубки или расчистки и непосредственно перед разрушением блока NPC проверяет разрешение на руду, присутствие другого живого существа и безопасность выхода. Опора самого рабочего и крепление его лестницы защищены также в обычных заданиях MINE / FORESTRY / CLEARING вне планов выемки. Проверка выхода из плановой шахты сохраняет прежний поиск пути с исключением удаляемого блока и его опоры.

Защита других существ учитывает фактическую высоту коллизии: полный блок, плита, ограда и другие выступающие формы. Живые NPC, игроки и мобы приостанавливают добычу занятого блока; наблюдатели не мешают. При освобождении участка работа продолжается, опасный выход или неразрешённая руда освобождают резервирование задания для дальнейшего планирования. Добыча не накапливает прогресс, пока начальная проверка запрещает работу.

В раскрытой карточке и обзоре Citizen показана причина: нет пути к работе, участок занят, требуется разрешение на руду или небезопасный выход. При прямом приказе и успешном начале другой работы старое сообщение сбрасывается; неактуальная причина у свободного NPC исчезает через шесть игровых секунд. Диагностика не сохраняется отдельно от мира и пересчитывается после загрузки. При переносимом грузе компактная карточка отдаёт приоритет строке доставки, полный обзор показывает обе причины.

Ручные проверки нужны для обычной добычи собственной опоры, плит/ограждений под другим NPC, занятого игроком блока, восстановления выхода и разрешения руды.

### Каталог домов (alpha.38)

Читатель MineColonies/Structurize `.blueprint` v1, серверный каталог, предпросмотр выбранного дома и явные ванильные замены служебных/декоративных блоков. [Установка и ограничения](BLUEPRINTS.md). [Отдельный стартовый пакет: два дома Medieval Oak](blueprint-packs/stonebanner-minecolonies-medievaloak.zip). Сборка и игровые проверки этой версии отложены.

### Временные строительные леса (alpha.39)

При недоступности рабочего места NPC строит под собой столб во время прыжков, добавляет боковую лестницу и прокладывает мосток. После использования разбирает свои временные блоки с возвратом материалов и контролируемым спуском. Требуются булыжник и лестницы на складе. [Правила и ограничения](CONSTRUCTION.md). Реализация добавлена; сборка и игровые проверки отложены.

### Единая схема ввода (alpha.40)

Контексты GUI → строительство → designation → приказы → использование предмета → WASD/Mouse
поглощают ввод по приоритету. Щелчок по HUD не передаёт действие в мир. Окна, чат,
контейнеры и пауза отменяют активное действие; маршрут героя приостанавливается.
Tab приостанавливает маршрут героя на время приказов, обратное переключение позволяет
продолжить. V сохраняет маршрут до нового ручного направления/приказа. Натяжение
отменяется без выстрела при смене профиля, окна или слота; обычное отпускание стреляет.
Приказы NPC и выбранный слот не меняются при переключении профиля.

E открывает инвентарь, F меняет предмет во второй руке, 1–9 выбирают настоящий хотбар
во всех режимах. В приказах Ctrl+номер сохраняет группу, Alt+номер выбирает,
Shift+Alt+номер добавляет к выделению. Все эти действия используют сохранённые
Minecraft/Forge-привязки, а не жёсткие коды клавиш.

F4 открывает настройки Stone & Banner (Управление / Камера / HUD); кнопка
«Клавиши» открывает просмотр назначений и конфликтов, а «Изменить привязки»
в нём ведёт в стандартный редактор Minecraft. Tab, остановка группы,
поворот чертежа, высота и вращение камеры, группы и помощь переназначаются.
Альтернативное взаимодействие доступно отдельной привязкой, изначально без клавиши;
оно позволяет явно использовать объект вместо контекстной добычи. Старые TOML-профили
ACTION/HYBRID/TACTICAL и пользовательские привязки Minecraft не перезаписываются.

На момент alpha.40 двойной клик и безопасность маршрутов оставались в 1.5; их реализация
добавлена ниже в alpha.41. Меню альтернатив Alt+ПКМ остаётся отдельным пунктом 1.6.
Сборка, unit/GameTest и игровой прогон alpha.40 отложены; наличие кода не подтверждает
поведение в игре. Сценарии дальнейшего прогона: [CONTROLS.md](CONTROLS.md).

### Движение героя 1.5 (alpha.41)

В Mouse одиночный ЛКМ идёт к точке, удержание обновляет цель максимум раз в 5 тиков,
отпускание оставляет последнюю допустимую цель. Неизменная цель не запускает повторный
A*. Двойной клик по обычной земле с интервалом до 300 мс и смещением курсора до 6 GUI
пикселей включает бег; Shift+клик сохраняет осторожный темп до нового маршрута.
Клики по UI, сущностям, интерактивным объектам, очередям и смена режима не составляют
двойной клик. Пустая рука + ПКМ по верхней стороне обычной земли останавливает путь;
логи/руда и активные инструменты сохраняют предметные действия. Alt+ЛКМ сохраняет очередь.

Оба профиля получают нормализацию диагонали и плавную подачу ванильного ввода, маршрут
замедляется перед конечной точкой. Изменение размеров/позиции героя, телепорт и прямые
записи скорости не используются. Автоматическое движение проверяет опасные блоки,
опоры, границы, загрузку чанков, водные участки и занятые NPC проходы; три попытки обхода
используют задержки 20/40/80 тиков. Причина отказа отображается в HUD и красным маркером.
Новая невалидная цель удержания не стирает прежний допустимый маршрут.

Глубоководный автоматический заход с берега запрещён; мелководье с воздухом над ним
допустимо. Уже плывущий герой получает горизонтальную цель на текущей глубине; Space и
Shift меняют глубину вручную, Ctrl ускоряет плавание. Прямой водный отрезок проверяется
реальным объёмом игрока; препятствие останавливает движение. При воздухе ниже 60 тиков
автоматический маршрут прекращается — всплытие через потолок не обещается.

TOML: `controls.doubleClickRun`, `doubleClickMs` (150–600), `heldPathInterval` (5–20).
Задержка отказа той же цели — 20 тиков; сообщения ограничены одним на 40 тиков.
Герой использует новую безопасную политику, а существующие рабочие маршруты NPC не
заменяются. NPC рассматриваются как временные препятствия в пределах 8 блоков.

**Сборка, unit/GameTest и клиентский прогон alpha.41 отложены.** Подготовлены
HeroMovementRulesTest, HeroNavigationGameTests и сценарии в [CONTROLS.md](CONTROLS.md).

### Предметы, добыча, строительство и бой 1.6 (alpha.42)

В исходниках добавлены меню Alt+ПКМ с проверкой снимка цели, защита союзников
на клиенте и сервере, фиксация цели удерживаемого действия, реальный ванильный
прогресс добычи и индикатор натяжения лука. Преследование ограничено 200 тиками,
24 блоками удаления от начальной позиции и 32 блоками до цели. Атаки сохраняют
ванильные дальность, видимость и cooldown; поворот к противнику ограничен по шагу.
Добыча в пределах reach может продолжаться дольше лимита подхода.

Прозрачные объёмы показывают форму устанавливаемого блока и клеток blueprint.
Для предмета-блока проверяются место, высота мира, граница, коллизии, выживание
состояния, базовые права и реальный reach/LOS; окончательное решение принимает
сервер Minecraft и его хуки защиты. Shift+колесо вращает размещение без zoom;
модификатор переназначается. R также сохраняет поворот blueprint.

Назначить работу можно выбранным гражданам по брёвнам/руде. В Orders меню
вызывается переназначаемой клавишей «Меню альтернативных действий»; Alt+ПКМ
сохраняет очередь перемещения. Валка всего дерева не включена: ломается выбранный
блок. **Сборка, unit/GameTest и ручной игровой прогон alpha.42 отложены.**
