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
```

## Current prototype

Version: `0.1.0-alpha.14`.

- Forge project foundation and dedicated client configuration.
- Action, tactical, and hybrid control modes.
- In-game mode cycling with `V` (rebindable in Minecraft controls).
- Third-person perspective enforcement.
- Smooth mouse-wheel camera zoom from 2 to 24 blocks.
- Configurable camera height and smoothing.
- Camera collision uses Minecraft's block-aware zoom check.
- Independent camera yaw and pitch while holding the middle mouse button.
- WASD movement is transformed relative to the camera direction.
- Tactical mode opens a free world cursor without pausing the game.
- Tactical raycasts select blocks under the real cursor instead of the central crosshair.
- Left click issues a direct movement command; right click stops it.
- The tactical overlay shows the hovered block and current movement state.
- Tactical movement uses a bounded A* route over walkable blocks.
- Routes can climb and descend one block, avoid basic hazards, and rebuild when the player is stuck.
- Route nodes and the final destination are rendered directly in the world.
- The tactical cursor resolves entities in front of blocks and shows their names.
- Hovered and selected entities receive different world-space outlines.
- Clicking a hostile mob issues an approach-and-attack command.
- Clicking a villager approaches and interacts; other entities are safely selected.
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
- Excavation safety queries require an exact route through the access structure to an exterior cell, avoiding the block being mined and ladder attachments that would be removed. Automated tests and the build pass; in-game verification is pending. See the roadmap.

The pathfinder now has prototype handling for stairs, bottom slabs, water, wooden doors, ladders,
trapdoors and open iron doors. Closed iron access still requires a player-operated redstone mechanism.
Combat currently delegates one contextual attack to vanilla Minecraft; the custom real-time combat
system and settlement simulation are later roadmap stages.

## Основание лагеря через баннер

Любой напольный ванильный баннер остаётся декором при установке. Нажмите ПКМ пустой рукой, выберите «Основать лагерь», введите имя и подтвердите. Shift обходит меню; в Tactical меню открывается нажатием на баннер. Цвет и узор не влияют на основание.

Один лагерь на игрока в измерении получает территорию 3×3 чанка, без пересечения с другими общинами и рядом с деревнями. Разрушение флага сохраняет общину и жителей. Поставьте другой баннер внутри прежних границ и подтвердите назначение центром: территория и дома не перемещаются.

Для прототипа поселения нужны минимум один принятый NPC с ролью `SETTLER`, кровать каждому жителю и игроку, зарегистрированный склад и по четыре съедобных предмета на человека. Выберите переселенца в Tactical и примите через меню знамени. Учитываются только загруженные кровати, реальные предметы и сущности; зарегистрированные жители вне загруженных чанков тоже включены в требуемые запасы. Полная вербовка через репутацию ещё не реализована.

В текущем прототипе склад регистрируется операторской командой `/stonebanner storage add X Y Z`; роль переселенца задаётся командой `/stonebanner npc participation <npc> settler`. Игровые найм и регистрация склада через интерфейс остаются в роадмапе.

## Слои территории и геология

В Tactical три кнопки под скоростью времени включают границы общин, ресурсы чанков и плодородные земли. Горячие клавиши во всех режимах: **F6 / F7 / F8** (переназначаются). Границы можно совмещать с любым слоем; ресурсы и плодородие переключаются между собой. Ресурсный слой окрашивает поверхность, показывает подписи внутри ближайших чанков и сведения о чанке под курсором.

Исследовательский стол: крафт `книга — железный слиток — книга`, затем два ряда досок. Поставьте его в своей территории и откройте ПКМ либо щелчком в Tactical. Исследования общие для лагеря/поселения, оплачиваются реальными предметами из инвентаря основателя и требуют работы рядом со столом (8 блоков). Прогресс сохраняется; при уходе, выходе из игры или потере стола он приостанавливается. Другой стол внутри общины позволяет продолжить без повторной оплаты.

| Этап | Сведения после обследования | Бумага / книги / дополнительно | Работа |
|---|---|---|---|
| Старт | Только наличие признаков залежей | — | — |
| Основы геологии | Общая оценка богатства | 8 / 1 | 30 сек. |
| Поиск обычных руд | Уголь, медь, железо и кварц с оценкой | 16 / 1 | 60 сек. |
| Глубинная геология | Дополнительно наличие ценных руд, без их богатства | 24 / 1 / 2 золота | 90 сек. |
| Полное обследование | Богатство каждого вида руды | 32 / 1 / 1 алмаз | 120 сек. |

Примите жителя через знамя, выберите его в Tactical и назначьте геологом через меню стола. Для обследования включите ресурсы, выберите геолога и нажмите **Shift + ЛКМ по земле** нужного чанка. Он физически идёт к точке, работает 14–24 секунды в зависимости от навыка, получает опыт и добавляет сведения общине. Более высокий метод требует повторного обследования. Новая команда, опасность, критические потребности, выгрузка NPC или истечение времени прерывают незавершённое обследование без раскрытия данных. Завершённые обследования сохраняются вместе с миром.

Сервер считает реальные блоки руды, группируя обычную и глубинную разновидности. Для каждого вида свои пороги скудных/умеренных/богатых залежей. Скрытые названия, точные количества и подземные координаты не отправляются клиенту. Подсчёт ограничен 8192 блоками на измерение за тик, не загружает новые чанки; кэш обновляется после добычи игроком/NPC и периодически для сторонних изменений. При первой оценке возможна короткая задержка.

Плодородие сейчас — индекс пригодности реальной поверхности по почве, климату, влажности грядок и воде. Камень и водоёмы не подсвечиваются как плодородные. Это ещё не изменение скорости роста ванильных культур; истощение почвы и удобрения остаются в роадмапе.

## Камера под крышей и в шахтах

Камера проверяет весь путь от глаз игрока к приподнятой точке и затем назад к выбранному масштабу. Потолки, стены, плиты, лестничные блоки, двери и другие визуальные формы ограничивают движение с запасом для ближней плоскости камеры. В тесном месте фактическая дистанция может стать меньше минимального пользовательского зума; выбранные высота и масштаб сохраняются и восстанавливаются после выхода на открытое место. Коррекция столкновения применяется сразу, без задержки сглаживания. Переход к жиле тоже не проходит через стены; незагруженные чанки считаются препятствием.

Если сами глаза игрока оказались внутри геометрии и безопасной исходной точки нет, обзор мира временно закрывается, включая режим F1. Интерфейс остаётся доступным, а тактический курсор не выбирает объекты через препятствие. Это резервная защита для застревания/телепортации внутри блоков.

## Свободная тактическая камера и взаимодействия

В **Tactical** камера сохраняет собственную точку и не следует за героем или выбранным NPC. **WASD / стрелки** перемещают её, **Page Up / Page Down** меняют высоту, **Shift** ускоряет движение. **Средняя кнопка + движение мыши** вращает камеру, **Shift + средняя кнопка** перемещает её, колесо меняет зум. **Home** возвращает обзор к герою. Камера ограничена загруженным окружением (до 64 блоков от героя); проверки стен, потолков и незагруженных чанков сохраняются. Action и Hybrid продолжают следовать за героем.

- **ЛКМ по земле** — перемещение героя либо выбранного жителя.
- **ЛКМ по жителю** — выбор; **ПКМ по NPC** — герой подходит и взаимодействует. Для Human NPC открываются существующие сведения о жителе; для ванильных NPC используется обычное взаимодействие, включая торговлю.
- **ЛКМ по известному интерактивному блоку** или **ПКМ по блоку** — герой подходит и использует его: сундуки, верстаки, двери, кнопки, рычаги, знамя, исследовательский стол и другие ванильные/совместимые меню. ПКМ с предметом сохраняет обычное использование предмета; учитывается и вторая рука.
- **ЛКМ по враждебному мобу** либо **Alt + ЛКМ по цели** — приказ герою атаковать. Он преследует цель и наносит удары с ванильной перезарядкой до её смерти или замены приказа.
- **Space** — отменить приказ; ПКМ по пустому месту отменяет выбор/приказ.

Обзор камеры не даёт дистанционного доступа: сервер проверяет реальный радиус героя, препятствия, цель, границу мира и разрешения на блок. Используются реальные предметы и обычные механики Minecraft/Forge. Незавершённое взаимодействие приостанавливается при открытом меню; при смене мира команды сбрасываются.
