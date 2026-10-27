# NF-e Fácil 1.2.0 — fontes e modo noturno

Implementação conjunta dos comentários de acessibilidade e modo noturno da [issue #1](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1). Nome **NF-e Fácil**, pacote **br.com.pevicasi.nfefacil**, versão **1.2.0**, código **4**. APK universal com assinatura debug automática para uso pessoal; nenhum Secret permanente exigido, nenhuma variante QA entregue e nenhuma otimização/remoção de ABI. APK anterior preservado.

## Uso e persistência

- **A− / A+** ajustam de 90% a 160%, em passos de 10%; botões são desabilitados nos limites. **Restaurar padrão** volta a 100%, preservando o tema escolhido.
- **Tema: Claro / Escuro / Automático (Android)** mostra a seleção atual. Automático acompanha mudanças do modo noturno do sistema, sem recarregar a nota/consulta ou recriar a câmera. Claro/Escuro sobrepõem o tema do sistema somente no app.
- As duas preferências ficam em SharedPreferences privadas, separadas da sessão fiscal; sobrevivem à troca de telas e à reabertura. Não se limpa armazenamento, não se desinstala o app e não se migram dados para outro pacote.
- Controles de toque têm altura mínima de 48 dp, nomes acessíveis e texto que pode quebrar. O botão Restaurar usa a largura disponível e quebra o texto em telas estreitas. No scanner e na confirmação, o conteúdo pode rolar para não cortar controles/chaves. A câmera, fotografia e imagens OCR não são recoloridas. A seleção entre várias chaves usa uma lista própria rolável, com os 44 dígitos quebrando em linhas completas; nenhuma nota ambígua é selecionada automaticamente.

## Escopo técnico e preservação

`AppearanceActivity.java` aplica escala sem acumular multiplicações, cores nativas, barras do sistema, controles, diálogos próprios e mensagens. MainActivity e ScannerActivity compartilham preferências e acompanham mudanças de `uiMode` sem perder o estado atual. A recriação normal da Activity continua preservando a confirmação fiscal.

`android-appearance.js` só aceita a origem local HTTPS do app e as quatro páginas conhecidas. Ajusta fontes por papel, cartões, campos, botões, cabeçalhos/tabela e temas com cores próprias (sem filtro de inversão). A tabela de 16 colunas mantém rolagem horizontal e texto quebrável; o desconto usa verde contrastante também em escuro. Mudanças em tempo de execução não recarregam páginas ou consultas. Regras ficam em `@media screen`: impressão/PDF mantém apresentação clara e estilos originais.

Os quatro HTMLs do GitHub Pages não foram alterados nesta atualização. O site não carrega o asset de aparência, e a injeção recusa páginas externas. A navegação externa continua no navegador/outro app; diálogos do sistema Android, como permissões, seletor de arquivos e impressão, pertencem ao sistema.

Scanner CameraX/ML Kit/OCR, validação fiscal/múltiplos códigos, XMLs, ST/acréscimo, desconto, CSV e ponte de impressão são mantidos. A assinatura debug pode mudar entre runners; não se promete atualização entre certificados diferentes ou fim de alertas Play Protect.

## Validação reproduzível

| Comando / teste | Evidência exigida |
|---|---|
| `npm ci && npm test` | 13 testes: 10 regressões existentes e 3 de aparência. Quatro páginas, claro/escuro, 90/120/160%, 360×800 e 640×360; sem overflow da página/cortes em botões; tabela rolável; contraste de texto e desconto ≥4,5:1; dados/consulta preservados; impressão clara; rejeição de origens externas. Capturas em `test-results/appearance-*`. |
| `python3 -m unittest discover -s android/tools -p 'test_*.py'` | 5 testes de política de assinatura, com fixtures isoladas. |
| `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` | 8 unitários Java, lint e compilação do app/APK de instrumentação. Compilar instrumentação não equivale a executá-la. |
| `./gradlew connectedDebugAndroidTest` | 24 testes reais no emulador: 20 anteriores (incluindo ML Kit/OCR offline e desconto embarcado) + 4 de AppearanceTest: controles/limites/reset/reabertura, nota/preços com escala/tema combinados, scanner/confirmação e mudança real de modo noturno Android via `cmd uimode`. |
| `EXPECTED_VERSION_CODE=4 EXPECTED_ABI=universal python3 android/tools/verify-qa.py .../app-debug.apk` | Nome/pacote/versão, quatro ABIs, assinatura v2, SHA-256 e bytes dos quatro HTMLs + bridge/aparência comparados com o repositório. O nome histórico do script não indica variante QA; ele rejeita identidade `.qa`/`-qa`. |

O workflow registra contagens reais e checksum do APK em anotações públicas e guarda relatórios. Depois de build e instrumentação aprovados, um job separado usa apenas o token temporário do GitHub para publicar o APK em `v1.2.0` com checksum, certificado público e metadados. Ele arquiva o APK 1.1.0 original em `v1.1.0`, verificando seu checksum fixado, e não substitui releases existentes de outro commit. Nenhum Secret de assinatura é necessário; a chave continua debug automática. Capturas nativas de nota/preços/scanner são coletadas do emulador no artefato `device-test-reports`; capturas web ficam em `validation-reports`. Resultado do commit final, run ID, artefato/link e checksum são registrados na issue #1, sem usar o CI de uma versão anterior como prova desta atualização.

Limites: testes automatizados e imagens sintéticas não substituem conferência no telefone do proprietário, leitores de tela em hardware, impressão física, APIs governamentais reais, foco/flash real ou Play Protect. Nenhum desses resultados físicos será afirmado sem execução.
