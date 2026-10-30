# AGENTS.md — Radar de Ganhos

Instruções para quem (humano ou IA) for mexer neste repositório. Cada regra abaixo
corresponde a um bug que já aconteceu e foi corrigido: são justamente as coisas que
não devem ser desfeitas. O histórico completo — com o que foi tentado e **não**
funcionou — está no README, na seção “O que já foi tentado e não funcionou”.

## O que é

App Android em Kotlin, **sem servidor e sem rede**: captura a tela pelo serviço de
acessibilidade, roda OCR local (ML Kit) e mostra o card flutuante com R$/h e R$/km
líquidos da oferta do Uber/99. Também recusa oferta RUIM apertando o X do app de
origem, com contagem regressiva.

```
versão 1.24 · versionCode 25 · minSdk 30 · compileSdk/targetSdk 34 · JDK 17 · Gradle 9.6
```

Desde a 1.24 o app abre numa **Home com a calculadora do plantão** (bruto por app, km,
horas, custos, meta e saldo do dia) e tem três abas: Home, Config e Histórico. A leitura
do card continua igual — a calculadora é do dia, não de uma oferta.

## Comandos

| O que | Comando |
|---|---|
| Publicar versão (fluxo do dono, Windows) | `.\publicar.cmd -Resumo "o que mudou"` |
| Publicar sem token local (nuvem) | `git push origin HEAD:release` → workflow `.github/workflows/release.yml` |
| Testes | `sh gradlew :app:testDebugUnitTest` (156 testes) |
| Ver as telas sem instalar o APK | rode `TelasPngTest` e abra `app/build/screens/home.png` e `config.png` |
| APK assinado | `sh gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Só o parser, sem Android SDK | `kotlinc RideParser.kt NumberParse.kt main.kt -include-runtime -d h.jar` |

No Linux/nuvem use `sh gradlew`, não `./gradlew`: o bit de execução não vem no clone.
Build exige **JDK 17** e **Android SDK 34** (`ANDROID_HOME` ou `local.properties`).

Sem SDK instalado, ainda dá para conferir a leitura: compile
`app/src/main/java/com/radardeganhos/app/RideParser.kt` + `NumberParse.kt` com um
`main` de teste e um stub de `AutoScanPolicy` (só `familiaDe` é usado). Foi assim que
os cards reais foram validados.

## Regras que NÃO se quebram

1. **A leitura começa abaixo do card** (`AreaLeitura`), nunca a tela inteira: senão o
   saldo do app vira o valor da corrida.
2. **Valor = primeiro R$ do card que não é linha de anotação.** Nunca o maior R$ da
   tela (a 99 mostra ofertas alternativas embaixo) e nunca um R$ de bônus/taxa/tarifa
   (`valoresAnotados`). Sem valor de verdade no texto, o card não é lido.
3. **O card termina no botão de aceitar** (`Aceitar`, `Aceitar por R$…`,
   `Tenho interesse`, `Tentar novamente`, `Tentando encontrar (2s)`). O que vem
   depois não é daquele card.
4. **Dois endereços obrigatórios** (busca e desembarque); sem os dois, não é card.
   Palavra de parada sempre no plural também (`2 paradas`, `3 paradas`).
5. **Piso de leitura: R$ 0,59/km e R$ 20,75/h** — o Envios Carro mais barato já visto
   (R$ 10,03 por 17 km e 29 min). Comparação em centavos (`centavos()`), para o card
   que paga exatamente o piso não cair por arredondamento.
6. **A recusa automática olha só o veredito RUIM.** Os mínimos numéricos vêm **0**
   (desligados) de fábrica: com 35/h e 1,30/km ela recusava corrida boa.
7. **Sem servidor, sem rede, sem script remoto.** Nada de Lua/JSON baixado para
   executar dentro do `AccessibilityService`: quebra a promessa de privacidade do
   README e transforma o servidor em execução remota de código no celular.
8. **`allowBackup` fica `false`**: com `true`, as SharedPreferences (custos,
   histórico, mínimos) iam para a nuvem do Google, contra o que o README promete.
9. **Mudar padrão de fábrica exige migração**: `Prefs.migrar` só troca o valor que
   ainda está no padrão antigo, e `Prefs.VERSAO` precisa subir. Sem isso, quem já tem
   o app instalado nunca recebe o padrão novo (foi o caso da 1.14 e da 1.16).
10. **Um teste para cada bug corrigido**, com o texto real do card que falhou —
    é o que impede a regra de voltar atrás sem ninguém notar.
11. **APK não vai para o git** (`dist/` é ignorado): 50 MB por versão e o histórico não
    encolhe. O APK fica na release do GitHub.
12. **`lint.checkReleaseBuilds = false`** é proposital: o lintVital aborta o build em
    JDK 25. Não reative sem testar num JDK 25.
13. **Tempo do card é lido por `Duracao`, nunca por regex solta de minutos.** "1 h e
    19 min (71,7 km)" tem de valer 79 min: uma regex de `(\d+) min` casa só o "19"
    do fim da frase e o R$/hora sai absurdo. Foi o bug da 1.23 → 1.24.
14. **A Home é do dia e não pode perder configuração.** Preço do litro, consumo,
    reserva e meta sobrevivem à virada do dia e ao *Salvar plantão*; o resto zera.
    Todo campo que a tela mostra precisa ser preenchido ao abrir (`HomeController
    .preencher`), senão o primeiro dígito digitado em outro campo grava zero nele.
15. **Trocar de aba não marca item da barra dentro do callback da barra.** `mostrar()`
    roda com a guarda `trocandoAba`; sem ela o `selectedItemId` reentra no listener e
    estoura com `StackOverflowError`.

## Armadilhas do ambiente

- **Robolectric**: toda a suíte roda em `@Config(sdk = [34])`. Na 33, o jar
  `android-all-instrumented-13` não tem `BluetoothDevice$BluetoothAddress` e o teste
  que abre a tela de configurações morre com `NoClassDefFoundError`.
- **JDK 25**: exige Robolectric 4.17+ (4.12.2 dá “Unsupported class file major
  version 69” em todos os testes Robolectric).
- **GitHub nesta nuvem**: a API sem token responde 403/404, então não dá para criar
  release por aqui — o caminho é o workflow da branch `release`.
- **Release pelo `publicar.ps1`**: notas de várias linhas vão por arquivo (`git commit
  -F`), nunca por `-m`; e `target_commitish` é a branch, nunca o SHA (422).
- **`MaterialSwitch`/`SwitchCompat` no Robolectric**: medir um switch sem texto dá
  `NullPointerException` em `SwitchCompat.makeLayout` e derruba os testes das abas. A
  tela usa `Switch` do framework; o PNG das telas roda em `@GraphicsMode(NATIVE)` e
  falha se a tela sair com menos de 20 cores (tela em branco).

## Mapa dos arquivos

| Arquivo | Papel |
|---|---|
| `RideParser.kt` | OCR → card (valor, km/min de cada trecho, endereços, categoria, app) |
| `NumberParse.kt` | números do OCR (vírgula/ponto, `l` no lugar de `1`) |
| `RideAccessibilityService.kt` | serviço: captura, OCR, auto-leitura, recusa, overlays |
| `ScreenReader.kt` | ML Kit: captura → texto |
| `AreaLeitura.kt` | onde a leitura começa (abaixo do card) |
| `Recortes.kt` | divide a 2ª tela nas janelas do Uber/99 |
| `OverlayController.kt` | botão R$, card flutuante, mira do X |
| `PanelBinder.kt` | preenche o card com o resultado |
| `Calculator.kt` | custos, líquido, nota, mínimo de uma oferta |
| `Duracao.kt` | tempo do card ("1 h e 19 min", "1h19") → minutos |
| `Plantao.kt` | cálculo do dia (bruto, despesas, saldo, R$/km, R$/h, meta) + gravação |
| `HomeController.kt` | aba Home: campos, chips, horários, meta, salvar plantão |
| `ColorRules.kt` | semáforo de cor |
| `Recusa.kt` | decide e clica no X |
| `Settings.kt` | `Settings` (padrões) + `Prefs` (gravação e migração) |
| `AutoScanPolicy.kt` | famílias de app e intervalos de leitura |
| `RideHistory.kt` / `HistoryActivity.kt` | histórico e prints |
| `AutoDiag.kt` | texto do diagnóstico na tela |
| `MainActivity.kt` | abas Home/Config/Histórico e comandos da leitura |
| `tab_home.xml` / `tab_config.xml` | conteúdo das duas abas dentro do `activity_main.xml` |
| `CardConfigActivity.kt` / `CustosActivity.kt` | telas de ajuste do card e dos custos da oferta |
