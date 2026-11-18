# MANU Launcher 3.7.1

Исправления по аудиту (verify, whitelist, state, MSAA, permissions).

## Fixes
1. Успех apply **только** если verified путь содержит `com.roblox.client`
2. `verifyFile` — сравнение нормализованного JSON (не «есть FPS»)
3. Elevated verify через root/shizuku copy-back для `/data/data`
4. Whitelist: BLOCKED реально проверяется; неверный тип → drop, не silent coerce
5. MSAA: `-1` Auto, `0` Off, `>0` samples
6. Reset — полный сброс всех полей State
7. saveState/loadState — все поля + game mode
8. JSON editor идёт через writeFlagsDetailed + report + permission check
9. applyFlagsOnly не продолжает запись/launch пока нет «Все файлы»
10. clear cache без слепого `echo OK`

Delta package = `com.roblox.client`.
