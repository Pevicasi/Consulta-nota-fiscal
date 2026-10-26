# Registro de alterações

## 1.0.1 — Android / issue #2

- Adicionada moldura de cantos arredondados com interpolação sobre a geometria real do ML Kit, transformação CameraX de rotação, viewport/recorte e espelhamento da prévia. Sem detecção, usa somente guia estática; geometria antiga expira. Animação respeita a desativação de animações do sistema.
- Mantidos os modelos embarcados CODE 128/OCR, foco, pinça, zoom manual, lanterna e recorte. Sem zoom automático para evitar interferência nos controles manuais.
- Chaves válidas exibidas em texto selecionável, com botões explícitos “Confirmar chave” e “Ler novamente”, bloqueio de confirmações duplicadas e restauração do diálogo na recriação da tela.
- Adicionados testes instrumentados de confirmação/retorno, nova leitura, recriação e transformação de coordenadas em quatro rotações com recorte e espelhamento. Site preservado.

## 1.0.0 — Android / issue #1

- Auditado o site atual, suas quatro páginas, interface móvel/desktop, dependências e limitações em `docs/AUDITORIA.md`.
- Adicionado aplicativo independente em `android/`, mantendo as páginas HTML originais como fonte única dos cálculos e da interface. Os assets são gerados no build.
- Implementados CameraX, foco contínuo e por toque, zoom, flash disponível por hardware, troca de câmera, galeria, orientação EXIF e editor de recorte.
- Implementados ML Kit CODE 128 e OCR latino com modelos locais, validação da chave de 44 dígitos por módulo 11, deduplicação e confirmação do resultado.
- Integrados seletor de XMLs, clipboard Android, exportação CSV, impressão/PDF, navegação e geolocalização com permissões; ponte restrita à origem local HTTPS.
- Corrigida a exceção `torchBtn` no site com guardas para quatro elementos opcionais, sem alterar cálculos, estilos ou páginas de preços.
- Adicionados testes de regressão web, testes unitários de validação de chaves e integração Android com modelos embarcados.
- Configurado GitHub Actions para testes, lint, build, verificação de assinatura, APK instalável com SHA-256, relatórios e testes em emulador. Instruções de instalação e limitações em `android/README.md`.

### Ajustes após validação

- Corrigido o provisionamento de CI para não solicitar o pacote SDK `tools`, removido do catálogo Android.
- Limitadas as resoluções de captura/análise e o tamanho de imagens da galeria para reduzir memória sem depender de desativação de recursos.
- Garantido que uma chave lida a partir da tela de preços volte à tela de notas, aguardando a ponte local ficar pronta. Adicionados testes desse retorno e das coordenadas/limites do editor de recorte.
- Declarados câmera/localização como recursos opcionais para preservar o uso de XMLs/galeria em aparelhos sem esses recursos; preservados os callbacks do WebView na rotação da tela.
- Registrada na auditoria uma falha pré-existente de apresentação de erros da Consulta 2, reproduzida com rede simulada nas três versões de preços.
