# JvmRamCostMonitor

Кроссплатформенный монитор потребления RAM процессами, аналогичный VisualVM, но с дополнительными метриками offheap памяти.

## Возможности

- **Кроссплатформенность**: Linux и Windows
- **Метрики памяти**:
  - Linux: RSS, PSS, USS
  - Windows: Working Set, Private Bytes
  - JMX: NMT, Used Heap, Committed Heap
- **Управление Java процессами**: GC
- **Графики**: отображение всех метрик на едином графике

## Требования

- сборка -jdk25 минимальная по размеру, но требует установленной JDK 25
- сборка -standalone чуть больше, но включает в себя JDK

## Использование

### Linux

Запустите приложение:
```bash
java -jar app.jar
```

*Внимание!* на некоторых машинах требуется запускать так:
```bash
WEBKIT_DISABLE_COMPOSITING_MODE=1 jvm-ram-cost
```

### Windows

Запустите приложение через стартовое меню или с помощью ярлыка на рабочем столе.

### Интервалы опроса

- RSS (Linux): 5 сек
- PSS/USS (Linux): 30 сек
- Working Set (Windows): 5 сек
- Private Working Set/Private Bytes (Windows): 10 сек
- JMX метрики: 5 сек


## Примечания

- JMX подключение работает только для локальных процессов
- Для работы JMX функций (GC, Heap Dump) процесс должен быть запущен с соответствующими опциями JMX

## О специфичных для Linux метриках

### RSS — Resident Set Size

RSS (Resident Set Size) — это объём физической RAM, который в данный момент реально занят процессом.

Что входит в RSS:

- реально загруженные страницы heap;
- реально используемые страницы stack;
- загруженные страницы shared libraries;
- mmap-области, которые были затронуты.

Что не входит:

- swap (в большинстве утилит);
- неиспользуемые, но зарезервированные страницы;
- mmap'ы, которые ни разу не были page-fault'нуты.

Важно:

- RSS включает shared pages целиком, даже если они делятся между процессами;
- поэтому RSS ≠ «уникальная память процесса».

#### Для JVM RSS — это:

- наиболее близкий показатель к «сколько RAM реально занято сейчас»;
- именно RSS начинает «давить» систему и триггерить OOM-killer;
- ключевая метрика для контейнеров и cgroups.


### USS — Unique Set Size

USS — объём памяти, уникально принадлежащей процессу.

Формально:

```
USS = sum(private_clean + private_dirty)
```

Что входит:

- приватные heap-страницы;
- приватный metaspace;
- thread stacks;
- direct buffers;
- native malloc;
- JIT code (если не shared).

Что не входит:

- любые shared pages (даже если процесс их единственный пользователь);
- shared libs (libjvm.so, libc.so);
- mmap-файлы.

Интерпретация

USS ≈ «если этот процесс убить, сколько RAM освободится немедленно».

#### Для JVM USS — это:

- самый честный показатель «реальной цены» процесса;
- лучший индикатор native-утечек.

### PSS — Proportional Set Size

PSS — компромисс между RSS и USS.

Идея:

- shared-page делится пропорционально между процессами;
- если страницу используют N процессов — каждому засчитывается 1/N.

Формально:

```
PSS = USS + sum(shared_pages / sharing_count)
```

Интерпретация

PSS ≈ «справедливая доля RAM процесса».

Используется:

- OOM-killer’ом;
- systemd-cgroup accounting;
- контейнерами;
- Kubernetes.

#### Для JVM PSS — это:

- PSS — лучшая метрика для оценки реального давления на систему;
- именно по PSS корректно сравнивать несколько Java-процессов.

## О специфичных для Windows метриках

### Working Set (WS)
Working Set в Windows ≈ RSS в Linux

Working Set — это объём физической памяти, который в данный момент резидентен в RAM для процесса.

Что включает:

- heap (используемые страницы);
- stack;
- загруженные DLL;
- mmap’нутые файлы;
- shared pages — учитываются полностью, как и в RSS.

Что не включает:

- страницы, выгруженные в pagefile;
- зарезервированную, но не затронутую память.

Где смотреть

- Task Manager → Memory
- Performance Counter: `Process(*)\Working Set`
- API: `GetProcessMemoryInfo → .WorkingSetSize`

Вывод

Working Set — _оперативный аналог RSS_, с теми же искажениями:

- shared pages считаются целиком;
- сумма WS по системе может превышать RAM.

### Private Bytes (PB)

Private Bytes в Windows ≈ USS в Linux + swapped private memory

Private Bytes — объём виртуальной памяти, выделенной эксклюзивно процессу, независимо от того:

- в RAM она,
- или в pagefile.

Где смотреть:

- Task Manager → Commit size (старые версии)
- Performance Counter: `Process(*)\Private Bytes`
- API: `GetProcessMemoryInfo → .PrivateUsage`

#### Для JVM Private Bytes — это

PB — самая честная метрика “стоимости процесса” в Windows.