# Radar de Ganhos

APK Android que lê o card de corrida do **Uber** e da **99** em qualquer tela (app, print, foto, post do Facebook) e mostra, em 1–2 segundos, **quanto vale a corrida por hora e por km já descontando os custos**.

## APK pronto

`dist/RadarDeGanhos-1.2.apk` — 50 MB, assinado (esquema v2), Android 11+ (API 30).
Instala por cima da 1.1, mantendo as configurações salvas.

```
pacote: com.radardeganhos.app
versão: 1.2 (versionCode 3)
```

## Instalação

1. Copie o APK para o celular (WhatsApp, Drive, cabo USB).
2. Toque no arquivo e permita "instalar de fontes desconhecidas" para o app usado na cópia.
3. Abra o **Radar de Ganhos**.
4. Toque em **Abrir Acessibilidade e ativar** → Ajustes › Acessibilidade › Apps instalados › **Radar de Ganhos** → ativar.
5. O botão flutuante **R$** aparece por cima de qualquer app.

## Como usar

- **Card do Uber/99**: quando o card aparecer, toque no **R$** (ou espere a auto-análise, ligada por padrão).
- **Foto, print, WhatsApp, Facebook**: deixe a imagem na tela e toque no **R$**. O app lê a tela inteira, não só os apps de corrida.
- **Painel**: toque em **↻ Reler** para reler, **✕** para fechar; arraste o painel pelo cabeçalho e o botão R$ para onde quiser.
- Sem o botão flutuante, a tela do app também analisa **foto da galeria** e **texto colado**.

## O que o painel mostra

- **Gera por hora** e **por km nesta corrida** em destaque — os valores da oferta atual, com semáforo de cor: **vermelho** = ruim, **branco** = neutro, **verde** = bom.
- Logo abaixo, **lucro líquido da corrida**: `valor − custos`, com o líquido por hora e por km.
- Trajeto: deslocamento até o cliente e viagem (km e tempo de cada um), km usado no custo.
- Combustível, manutenção, comissão, custo fixo e custo total.
- **Mínimo que a corrida precisaria pagar** para bater sua meta, e a diferença que falta.
- Nota de 0 a 100% (100% = exatamente na meta) e veredito.

Todos os itens podem ser ligados/desligados individualmente nas configurações.

Veredito: **ÓTIMA** (≥120% da meta) · **BOA** (≥100%) · **MÉDIA** (≥80%) · **RUIM** (<80%).

### Cores do R$/hora e do R$/km

| Métrica | Vermelho (ruim) | Branco (neutro) | Verde (bom) |
|---|---|---|---|
| R$/hora | até 35,00 | 35,01 a 44,99 | 45,00 ou mais |
| R$/km | até 1,30 | 1,31 a 1,79 | 1,80 ou mais |

Os quatro limites são editáveis na seção **4 · CORES** da tela do app (o padrão acima é o que vem de fábrica).

## Configurações (tela do app)

| Campo | Usado para |
|---|---|
| Preço do litro / consumo km/l | custo de combustível por km |
| Manutenção R$/km | óleo, pneu, freio, revisão, depreciação |
| Comissão do app % | quando o valor do card não vem líquido |
| Aluguel/fixo R$/hora | locação, diária, seguro rateado |
| Espera extra (min) | tempo parado no embarque |
| Retorno extra (%) | km de volta ao ponto forte |
| Meta R$/km e R$/hora | base do veredito e do valor mínimo |
| Auto-análise | leitura automática quando um card aparece no Uber/99 |
| Espera entre leituras (s) | intervalo mínimo entre duas auto-análises (2–120 s) |
| Ficar na tela (s) | quanto tempo o card fica aberto; **0** = só fecha no ✕ |
| Tamanho do card | Pequeno 0,85× · Normal 1× · Grande 1,15× · Extra 1,3× |
| Itens do card | veredito, R$/hora, R$/km, valor/km/tempo, lucro líquido, custos, trajeto, meta, botão reler |
| Limites de cor | R$/hora vermelho até / verde a partir de, R$/km vermelho até / verde a partir de |

## Histórico de corridas

Cada card analisado (botão flutuante, auto-análise ou teste de texto/foto) entra no **histórico**, na seção **5** da tela inicial. Cada item mostra:

- valor total da corrida;
- **R$/km** e **R$/hora**, pintados com as mesmas regras de cor do card;
- **busca** (endereço de origem) e **desembarque** (endereço de destino), quando o card mostra o trajeto;
- lucro líquido estimado, app de origem e data/hora da leitura.

O topo resume a quantidade de corridas e as médias de R$/hora e R$/km. O histórico fica em `SharedPreferences` no próprio aparelho (até 300 corridas), sem servidor; o mesmo card lido duas vezes em até 2 minutos conta como uma só leitura, e o botão **Limpar histórico** apaga tudo.

## Cálculo

```
km total   = deslocamento + viagem  (+ retorno %)
tempo      = deslocamento + viagem  (+ espera)
combustível = km × preço_litro / consumo
manutenção  = km × R$/km
fixo        = horas × R$/hora
líquido     = valor − (combustível + manutenção + comissão + fixo)
R$/h        = líquido / horas
R$/km       = líquido / km total
nota        = min(R$/km ÷ meta_km, R$/h ÷ meta_h) × 100
valor mínimo = max(meta_km × km, meta_h × horas) + custos
```

Os cards são lidos com regras próprias para os dois formatos que aparecem nas suas capturas:

- Uber: `R$11.65`, `R$2.24/km est.`, `2 min (0.3 km) de distância`, `Viagem de 12 min (4.9 km)`, `1 h e 43 min (95,7 km)`
- 99: `R$54,89`, `R$2,20 por km`, `5Min (2,5km)`, `32Min (6,3km)`, `Dinheiro`, `Taxa de deslocamento`

## Privacidade

O OCR é o ML Kit embarcado, roda no próprio aparelho. O app não tem servidor, não guarda capturas e não envia texto para lugar nenhum.

## Recompilar

Requisitos: JDK 17, Android SDK 34, Gradle 8.7.

```bash
echo "sdk.dir=/caminho/do/android-sdk" > local.properties
gradle :app:assembleRelease      # gera app/build/outputs/apk/release/app-release.apk
gradle :app:testDebugUnitTest    # 37 testes: parser, cálculo, card, toggles, cores, endereços, histórico, auto-análise, inflação das telas
```

Assinatura: `keystore/radar.jks`, alias `radar`, senha `radar2026` (definida em `app/build.gradle.kts`). Mantenha o mesmo keystore para atualizar o app no celular sem desinstalar.

## Limites conhecidos

- Exige **Android 11+** para capturar a tela via serviço de acessibilidade (sem pedir MediaProjection).
- Em apps com proteção de tela (`FLAG_SECURE`, típico de bancos) a captura vem preta — os cards do Uber/99 não usam essa proteção.
- A qualidade do OCR depende da fonte e do zoom do card; em card muito pequeno, aproxime antes de tocar no R$.
