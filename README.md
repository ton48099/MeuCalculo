# Radar de Ganhos

APK Android que lê o card de corrida do **Uber** e da **99** em qualquer tela (app, print, foto, post do Facebook) e mostra, em 1–2 segundos, **quanto vale a corrida por hora e por km já descontando os custos**.

## APK pronto

`dist/RadarDeGanhos-1.0.apk` — 50 MB, assinado (esquema v2), Android 11+ (API 30).

```
pacote: com.radardeganhos.app
versão: 1.0 (versionCode 1)
sha256: e6bc3bd577f7c2d4f9d45241bb6bf52b5996f604da0d582fd733b4601fc97883
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

- **R$/hora líquido** em destaque e **R$/km líquido**, coloridos pelo veredito.
- Bruto por hora e por km (para comparar com o que o app informa).
- Valor da corrida, km total (deslocamento + viagem), tempo total.
- Combustível, manutenção, comissão, custo fixo, custo total e sobra.
- **Mínimo que a corrida precisaria pagar** para bater sua meta, e a diferença que falta.
- Nota de 0 a 100% (100% = exatamente na meta).

Veredito: **ÓTIMA** (≥120% da meta) · **BOA** (≥100%) · **MÉDIA** (≥80%) · **RUIM** (<80%).

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
gradle :app:testDebugUnitTest    # 15 testes: parser, cálculo, inflação das telas
```

Assinatura: `keystore/radar.jks`, alias `radar`, senha `radar2026` (definida em `app/build.gradle.kts`). Mantenha o mesmo keystore para atualizar o app no celular sem desinstalar.

## Limites conhecidos

- Exige **Android 11+** para capturar a tela via serviço de acessibilidade (sem pedir MediaProjection).
- Em apps com proteção de tela (`FLAG_SECURE`, típico de bancos) a captura vem preta — os cards do Uber/99 não usam essa proteção.
- A qualidade do OCR depende da fonte e do zoom do card; em card muito pequeno, aproxime antes de tocar no R$.
