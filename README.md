# Radar de Ganhos

App Android que lê o card de corrida do **Uber** e da **99** em qualquer tela (app, print, foto, post do Facebook) e mostra, em 1–2 segundos, **quanto vale a corrida por hora e por km já descontando os custos**.

## Versão atual

```
pacote: com.radardeganhos.app
versão: 1.23 (versionCode 24)   ·   Android 11+ (API 30)   ·   assinado (esquema v2)
```

O APK sai do próprio Android Studio (aba **Build › Build App Bundle(s) / APK(s) › Build APK(s)**)
ou pela linha de comando em `app/build/outputs/apk/release/`. Instala por cima das versões
anteriores mantendo as configurações salvas.

## Publicar uma versão nova

Um comando compila e publica (use `-Simulacao` para ver o plano sem alterar nada):

```
.\publicar.cmd -Resumo "o que mudou" -Notas "notas que aparecem na release"
```

O script soma 1 no `versionCode`, atualiza o `versionName`, gera o APK assinado em `dist/`,
commita, faz push, cria a tag `v<versão>`, abre a release no GitHub e anexa o APK.
O APK fica **na release**, não no repositório: `dist/` é ignorado pelo git desde a 1.23
(eram 50 MB por versão e o histórico do git não encolhe depois). Para publicar sem o
token da máquina, empurre a branch `release` — o workflow do GitHub Actions compila e
cria a release sozinho.

| Opção | Para quê |
|---|---|
| `-Versao 1.15` | escolhe o número (padrão: sobe o último, `1.15` → `1.16`) |
| `-Notas "..."` ou `-NotasArquivo notas.md` | texto da release |
| `-Simulacao` | mostra tudo sem build, commit ou envio |
| `-SemBump` | publica a versão que já está no `build.gradle.kts`, sem somar 1 |
| `-SemPush` | para antes do GitHub: só o commit local é feito |
| `-PularBuild` | reaproveita o último APK compilado |

Rode sempre pelo `publicar.cmd`: o Windows bloqueia `.ps1` por padrão (política de execução),
e o `.cmd` já contorna isso sem mudar configuração do sistema.

## Instalação

1. Copie o APK para o celular (WhatsApp, Drive, cabo USB).
2. Toque no arquivo e permita "instalar de fontes desconhecidas" para o app usado na cópia.
3. Abra o **Radar de Ganhos**.
4. Toque em **▶ ATIVAR LEITURA DE TELA** → Ajustes › Acessibilidade › Apps instalados › **Radar de Ganhos** → ativar.
5. O botão flutuante **R$** aparece por cima de qualquer app. O mesmo botão **desliga e religa** a leitura sem precisar voltar aos ajustes.

## Telas

O app tem menu inferior com **Início** e **Histórico**; na Início:

| Seção | O que tem |
|---|---|
| botão grande no topo | **ATIVAR / DESLIGAR** a leitura (ligação imediata) |
| **1 · CUSTOS** | preço do litro, consumo, manutenção, comissão, aluguel, espera, retorno e metas |
| **2 · CARD E CORES** | **leitura automática** (liga/desliga, intervalo, modo desktop, print e diagnóstico), duração na tela, **posição**, opacidade, tamanho da fonte, empilhar, itens visíveis, limites do semáforo e **recusar sozinho (o X)** |
| **STATUS** | se o serviço está ativo |

## Como usar

- **Card do Uber/99**: quando o card aparecer, toque no **R$** (ou deixe a leitura automática, ligada por padrão).
- **Foto, print, WhatsApp, Facebook**: deixe a imagem na tela e toque no **R$**. O app lê a tela inteira, não só os apps de corrida.
- **Painel**: toque nele para fechar; arraste pelo cabeçalho (segurar ali pausa o fechamento automático) e arraste o botão R$ para onde quiser.
- **Modo desktop (Smart Connect / DeX)**: ligue em *Card e Cores › Modo desktop* e o **botão R$ e o card vão para a 2ª tela** — inclusive com **Uber e 99 abertos lado a lado**, um card em cima do outro.

### Modo desktop (2ª tela)

Com a opção ligada, a cada ciclo o serviço procura um app de corrida em **qualquer display**
(`getWindowsOnAllDisplays`), captura aquela tela (`takeScreenshot(displayId)`) e lê o card lá. Regras:

1. modo desktop **desligado** → tudo acontece na tela principal (comportamento original);
2. ligado e Uber/99 aberto na 2ª tela → a leitura vai para essa tela;
3. ligado sem card na 2ª tela → volta para a tela principal.

**O botão R$ e o card aparecem na 2ª tela.** O overlay daquela tela é montado com a receita do próprio
Android (CTS do `AccessibilityService`): `createDisplayContext` + `createWindowContext`
(`TYPE_ACCESSIBILITY_OVERLAY`), anexado àquele display. Se o fabricante recusar a janela, o overlay
cai para o celular e a linha do **diagnóstico** diz “a tela externa recusou a janela do Radar” — nada
é perdido, o card simplesmente sai no aparelho.

**Dois apps lado a lado.** Com Uber e 99 abertos na mesma tela, o app recorta a janela de cada um
(`Recortes`) e lê separado: saem dois cards empilhados, um por oferta. Se não der para dividir com
segurança, volta ao OCR da tela inteira, como antes.

A captura passa pelo limite de frequência do Android (1 por segundo) e o relógio tem um watchdog de
25 s: se uma captura travar, a leitura se destrava sozinha em vez de parar até reiniciar o serviço.

## O que o painel mostra

- **Gera por hora** e **por km nesta corrida** em destaque — os valores da oferta atual, com semáforo de cor: **vermelho** = ruim, **branco** = neutro, **verde** = bom.
- Opcionalmente **R$/minuto**, com o semáforo próprio.
- **Busca** e **desembarque**, quando o card mostra o trajeto (sem inventar endereço).
- Logo abaixo, **lucro líquido da corrida**: `valor − custos`, com o líquido por hora e por km.
- Trajeto: deslocamento até o cliente e viagem (km e tempo de cada um), km usado no custo.
- Combustível, manutenção, comissão, custo fixo e custo total.
- **Mínimo que a corrida precisaria pagar** para bater sua meta, e a diferença que falta.
- Nota de 0 a 100% (100% = exatamente na meta) e veredito.

Cada item é ligado/desligado individualmente na tela **2 · CARD E CORES** — dá para esconder tudo e
mostrar só o que importa na hora.

Veredito: **ÓTIMA** (≥120% da meta) · **BOA** (≥100%) · **MÉDIA** (≥80%) · **RUIM** (<80%).

### Cores (semáforo)

| Métrica | Vermelho (ruim) | Branco (neutro) | Verde (bom) |
|---|---|---|---|
| R$/hora | até 35,00 | 35,01 a 44,99 | 45,00 ou mais |
| R$/km | até 1,30 | 1,31 a 1,79 | 1,80 ou mais |
| R$/minuto | até 0,58 | 0,59 a 0,74 | 0,75 ou mais |

Os limites são editáveis na tela **2 · CARD E CORES** (os valores acima são o padrão de fábrica),
e são os mesmos que pintam o card flutuante, o histórico e o resultado.

## Leitura automática

O serviço tem um relógio interno de 1,2 s e, a cada X segundos (intervalo configurado em
*Card e Cores*, padrão **2 s**, de 2 a 120), aperta o **botão de leitura manual sozinho** — em
QUALQUER tela (Uber, 99, print, galeria, Facebook…). Não existe lista de apps liberados: quem decide se há card na
tela é o parser depois da leitura, não o pacote do app. O fluxo é:

1. a cada intervalo ele dispara, **exceto enquanto o card de resultado está na tela**;
2. quando o card some, recomeça a contar o intervalo do zero;
3. lê de novo, sem repetir o mesmo card. (Fora do intervalo: só o botão manual.)

Cada tentativa deixa uma linha no **diagnóstico**, dentro de *Card e Cores* — e a linha se **atualiza
sozinha a cada 1,5 s** enquanto a tela está aberta: além da última leitura, mostra o que a automação
está fazendo *agora* (pronta, esperando o intervalo, card aberto na tela, leitura em andamento…). Se a leitura
parar, é ali que está o motivo.

O intervalo é **salvo enquanto você digita**, não só ao sair do campo: mude o número e ele já vale.

### Área lida: abaixo do card

O card nasce no **topo** da tela (padrão) justamente por causa disto: **a leitura começa na linha em que o
card termina e vai até o fim da tela**. O que fica acima dele não é lido.

Sem esse corte, o OCR lia a tela inteira e o parser — que pega o maior R$ do texto — devolvia o **saldo do app**
como se fosse o valor da corrida (e às vezes o próprio card do Radar). Agora o topo do app (saldo, menu,
notificação) e o card do Radar ficam fora da leitura. A folga e o limite saem da posição real da janela, então
o corte acompanha o card se você arrastá-lo para outro lugar.

Se o card ocupar quase toda a tela (fonte grande com todos os itens), não sobra área útil: nesse caso a leitura
volta a ser a tela inteira em vez de ler uma faixa de 20 px.

## Recusar sozinho (o X)

Quando a corrida é **RUIM** no card (abaixo da meta) ou não bate os mínimos configurados, o app **aperta o X
do card** por você — no celular e na 2ª tela (modo desktop). Vem **ligado de fábrica**, já clicando: a
contagem regressiva é a chance de cancelar. O botão **RECUSA SOZINHO** da tela principal liga e desliga a
função inteira sem entrar nas configurações.

Como funciona:

1. a oferta é lida e calculada normalmente; se o veredito não for RUIM e estiver dentro dos mínimos, nada acontece;
2. se for RUIM ou estiver fora dos mínimos, o card mostra a **contagem regressiva** (padrão **5 s**, de 1 a 30 —
   tempo de ler e conferir) e fica aberto até ela acabar; **tocar no card cancela** a recusa daquela oferta;
3. ao zerar, o app procura o X daquele app, **confere se a oferta ainda está na tela** (se você aceitou nesse
   meio-tempo, nada é clicado) e clica;
4. a mesma oferta nunca é recusada duas vezes (2 minutos de memória, por segurança).

O critério principal é o veredito que você vê: **RUIM recusa**. Os mínimos numéricos continuam valendo como
ajuste fino (servem, por exemplo, para recusar também uma MÉDIA) — abaixo de qualquer um deles, recusa
(0 desliga o critério):

| Critério | Padrão |
|---|---|
| veredito do card | **RUIM recusa** |
| km no total (pick-up + viagem) | desligado |
| minutos no total | desligado |
| R$ por hora (bruto) | desligado |
| R$ por km (bruto) | desligado |

O critério de fábrica é **só o veredito**: corrida BOA, MÉDIA ou ÓTIMA nunca é recusada. Os mínimos
numéricos são ajuste fino e vêm **desligados (0)** — na 1.15 eles vinham iguais ao vermelho do semáforo
(35,00/h e 1,30/km) e recusavam corrida boa; quem atualiza tem esses dois zerados automaticamente,
e quem já tinha ajustado um valor à mão mantém o valor.

**Onde fica o X.** O X do Uber não está no mesmo lugar do X da 99, e cada app desenha o botão do seu jeito.
A mira para ensinar tem o tamanho do próprio X (38 dp) e é arrastável: deixe o centro dela em cima do X
e toque em SALVAR. O app procura em três degraus: (1) o X que **você ensinou** — quando você recusa uma oferta à mão com o card
na tela, o app guarda a impressão daquele botão por app; (2) descrição/id que dizem “fechar”, “recusar”,
“decline”, “close”; (3) geometria (botão pequeno colado na borda de cima). Sem botão encontrado, **nada é
clicado** e o diagnóstico diz o motivo.

**Para testar sem risco**, toque em **RECUSA SOZINHO** na tela principal para desligar, ou marque **Só avisar**
nas configurações: a contagem aparece e nada é clicado. Para conferir o X, ligue a leitura e veja o diagnóstico
(“achei o X …”, “oferta conferida na tela”). Se o app não achar, recuse uma oferta à mão com o card na tela
que ele aprende — o diagnóstico confirma o aprendizado. Depois desligue o “só avisar” para valer.

> Recusar automaticamente é automatizar um toque dentro do app do Uber/99. Vai contra os termos de uso
desses apps e o risco (conta bloqueada, oferta recusada por engano) é seu. O app foi feito para ser
conservador: mínimo igual a 0 nunca recusa, leitura incompleta nunca recusa e a contagem sempre aparece
antes do clique.

## Histórico de corridas

Cada card analisado (botão flutuante ou auto-análise) entra no **Histórico**.
Cada item mostra:

- valor total da corrida;
- **R$/km** e **R$/hora**, pintados com as mesmas regras de cor do card;
- **busca** (endereço de origem) e **desembarque** (endereço de destino), quando o card mostra o trajeto;
- lucro líquido estimado, app de origem e data/hora da leitura;
- o **print da tela** no instante em que o card foi lido (miniatura no item; toque para ver em tela cheia).

O print é salvo como JPEG no próprio aparelho, reduzido para 1080 px de largura. A opção **“Salvar print da tela de cada corrida”** fica em *Card e Cores* e vem ligada; desligue se o armazenamento ou a memória pesarem — o histórico continua funcionando sem as imagens. **Limpar histórico** apaga as corridas e os prints.

O topo resume a quantidade de corridas e as médias. O histórico fica em `SharedPreferences` no próprio
aparelho (até 300 corridas), sem servidor; o mesmo card lido duas vezes em até 2 minutos conta como uma
só leitura, e o botão **Limpar histórico** apaga tudo.

## Cálculo

```
km total    = deslocamento + viagem  (+ retorno %)
tempo       = deslocamento + viagem  (+ espera)
combustível = km × preço_litro / consumo
manutenção  = km × R$/km
fixo        = horas × R$/hora
líquido     = valor − (combustível + manutenção + comissão + fixo)
R$/h        = valor / horas
R$/km       = valor / km total
R$/min      = valor / minutos
nota        = min(R$/km ÷ meta_km, R$/h ÷ meta_h) × 100
valor mínimo = max(meta_km × km, meta_h × horas) + custos
```

Os cards são lidos com regras próprias para os dois formatos que aparecem nas suas capturas:

- Uber: `R$11.65`, `R$2.24/km est.`, `2 min (0.3 km) de distância`, `Viagem de 12 min (4.9 km)`, `1 h e 43 min (95,7 km)`
- 99: `R$54,89`, `R$2,20 por km`, `5Min (2,5km)`, `32Min (6,3km)`, `Dinheiro`, `Taxa de deslocamento`

Corrida longa: `1 h e 19 min (71,7 km)` vira 79 min, inclusive quando o OCR troca o `1` por
`l`/`I` (`lh e 19 min`), come o `min` (`1h19 (71,7 km)`) ou separa os dígitos (`1 h e 1 9 min`).
Se o OCR inverter a ordem e a viagem longa vier antes do deslocamento, o app troca as duas.

O valor é sempre o **primeiro R$ do card** (o do topo), nunca o maior da tela: a 99 mostra ofertas
alternativas embaixo (`R$10,73  R$11,24  R$11,75`) e o maior delas tomava o lugar do valor real. O app
também é identificado pelo pacote, então o **99 Motorista** (`com.app99.driver`) aparece como `99` — antes
saía “app não identificado” porque o card dele não tem a palavra “99”.

O card é lido **até o botão de aceitar** (`Aceitar`, `Aceitar por R$…`, `Tenho interesse`, `Tentar
novamente`, `Tentando encontrar (2s)`): o que vem depois dele — as ofertas alternativas da 99, o mapa, o
texto do outro app — não entra na conta. Linhas que só **anotam** dinheiro (`R$3,40 Tarifa base dinâmica
incl.`, `R$1,70 por corrida longa incl.`, `Taxa de deslocamento R$1,11`, `Est. de R$1,25/km`) nunca viram o
valor da oferta: sem o valor de verdade no texto, o card não é lido — melhor nada do que uma corrida falsa
de R$ 3,40. As tags do topo viram a categoria do cabeçalho (`UberX`, `Confort`, `99Pop`, `Negocia`,
`Envíos Carro`, `Prioritário`, `Exclusivo`) e linhas de apoio (`2 paradas`, `CPF verif.`, `Perfil
Essencial`) não entram no endereço.

Um card lido ainda pode ser descartado por pagar pouco: o piso é **R$ 0,59/km e R$ 20,75/h**, o valor do
Envios Carro mais barato já visto (R$ 10,03 por 17 km e 29 min). Abaixo do piso em qualquer um dos dois não
aparece card nenhum — melhor nada do que uma corrida que não paga a viagem.

## Privacidade

O OCR é o ML Kit embarcado, roda no próprio aparelho. O app não tem servidor e não envia texto para
lugar nenhum. As capturas ficam apenas no armazenamento interno, e **só** quando a opção de print está
ligada — “Limpar histórico” apaga todas.

## Recompilar

Requisitos: JDK 17, Android SDK 34 (o wrapper usa Gradle 9.6).

```bash
./gradlew :app:assembleRelease      # gera app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # 128 testes: parser, cálculo, card, toggles, cores, endereços, histórico, prints, modo desktop, divisão da 2ª tela, auto-análise, área lida, padrões e migração, inflação das telas, recusa automática (só veredito RUIM de fábrica, critérios opcionais, escolha do X, contagem, X ensinado à mão por tela), valor do topo do card e 99 Motorista identificada pelo pacote
```

Se o Android Studio estiver instalado, o JDK dele serve (`C:\Program Files\Android\Android Studio\jbr`);
na linha de comando use `-Dorg.gradle.java.home=/caminho/do/jdk17`.

Assinatura: `keystore/radar.jks`, alias `radar`, senha `radar2026` (definida em `app/build.gradle.kts`).
Mantenha o mesmo keystore para atualizar o app no celular sem desinstalar.

## O que já foi tentado e não funcionou

Cada item aqui é uma tentativa real que foi **desfeita** (ou limitada) porque piorou
alguma coisa. Se for mexer no código, leia antes: são as armadilhas que já custaram
uma versão. As regras operacionais estão no [AGENTS.md](AGENTS.md).

| Tentativa | Por que não ficou | O que ficou no lugar |
|---|---|---|
| Ler a tela inteira no OCR | o saldo e o menu do app viravam o valor da corrida | leitura começa abaixo do card (`AreaLeitura`, 1.8) |
| Usar o **maior R$** da tela como valor | a 99 mostra ofertas alternativas embaixo e o maior delas tomava o lugar | primeiro R$ do card (1.16) e hoje o primeiro R$ **que não é anotação** (1.23) |
| Deixar o texto do card ir até o fim da tela | “Tentando encontrar (2s)” e as ofertas alternativas entravam no endereço/valor | corte no botão de aceitar (1.23) |
| Palavra de parada só no singular | o endereço de busca saía como “…São Leopoldo, 2 paradas” | `(?:s\|es)?` e lista maior de palavras (1.23) |
| Mínimos de recusa iguais ao vermelho do semáforo (35/h e 1,30/km) | recusava corrida BOA/MÉDIA — a reclamação de “recusou corrida boa” | recusa só no veredito RUIM, mínimos em 0 (1.16) + migração em `Prefs` |
| “Só avisar” como padrão da recusa | a contagem terminava sem clicar e parecia que a função não funcionava | clicar por padrão, com contagem para cancelar (1.14) |
| Piso de leitura de R$/h ≥ 25 e R$/km ≥ 1 | descartava o 99 Envíos Carro de R$ 10,03, que é card legítimo | piso no próprio card: R$ 0,59/km e R$ 20,75/h (1.23) |
| Retry automático quando não achava card (1.21/1.22) | travava a leitura do serviço | removido em `ca4b471` |
| Recortar a 2ª tela sem exigir pacote de corrida | a moldura do Smart Connect gerava um card falso na metade sem oferta | recorte só com pacote Uber/99 conhecido (1.12) |
| Usar a 2ª tela sempre que o modo desktop estava ligado | parou de ler o celular — regressão da 1.11 | 2ª tela só quando há app de corrida nela (1.12) |
| Scripts Lua remotos por app, como o GigU faz | exigiria servidor (quebra “nada sai do aparelho”), executaria código de fora dentro do `AccessibilityService` e pararia de funcionar offline | só a ideia do gatilho/âncora do card, em Kotlin (1.23) |
| Robolectric 4.12.2 | “Unsupported class file major version 69” em todo teste, com JDK 25 | Robolectric 4.17 |
| Robolectric em `@Config(sdk = [33])` | `NoClassDefFoundError: BluetoothDevice$BluetoothAddress` ao abrir a tela de configurações | toda a suíte em API 34 (1.23) |
| `lintVital` no build de release | aborta o build em JDK 25 apontando a própria string de versão | `lint.checkReleaseBuilds = false`, documentado |
| Commit com notas de várias linhas por `git commit -m` no PowerShell | o PowerShell quebrava os argumentos e o commit falhava (1.11) | `git commit -F arquivo` no `publicar.ps1` |
| Release com `target_commitish` = SHA | GitHub devolve 422 quando a tag já existe | usar a branch como alvo |
| Corpo da release enviado como bytes sem charset | 422 nas 1.11 e 1.12 | UTF-8 explícito no `ChamarApi` |
| `allowBackup="true"` no manifest | o backup do Android mandava as configurações e o histórico para a nuvem do Google | `allowBackup="false"` (1.23) |
| Guardar cada APK no repositório (`dist/`) | 50 MB por versão, e o histórico do git não encolhe | APK só na release; `dist/` ignorado (1.23) |

## Para quem for mexer no código

O [AGENTS.md](AGENTS.md) tem os comandos (inclusive rodar o parser sem Android SDK),
as regras que não podem ser desfeitas, as armadilhas do ambiente (Robolectric, JDK 25,
release pela nuvem) e o mapa dos arquivos. Comece por ele.

## Limites conhecidos

- Exige **Android 11+** para capturar a tela via serviço de acessibilidade (sem pedir MediaProjection).
- Em apps com proteção de tela (`FLAG_SECURE`, típico de bancos) a captura vem preta — os cards do Uber/99 não usam essa proteção.
- A qualidade do OCR depende da fonte e do zoom do card; em card muito pequeno, aproxime antes de tocar no R$.
- O endereço no histórico depende do OCR manter o endereço em linha própria logo abaixo do tempo/distância — é o formato dos cards das suas capturas; num layout diferente, o campo aparece como "não identificado".

