# NF-e Fácil 1.3.0 — largura de colunas, custos e unidade final

Entrega conjunta dos pedidos da issue #1: [colunas](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1#issuecomment-6444270520) e [IPI/custos/embalagens](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1#issuecomment-6444513715). Nome NF-e Fácil, pacote br.com.pevicasi.nfefacil, versão 1.3.0/código 6. APK universal, debug automático pessoal, sem Secrets de assinatura ou otimização de ABI. Releases anteriores preservadas até aprovação do proprietário.

## Mudança intencional do cálculo

O cálculo antigo vUnCom + ST/qCom é mantido somente como campo de compatibilidade do parser. **Nenhuma tela de preço final ou CSV usa esse cálculo antigo.** O motor compartilhado `fiscal-cost.js` é usado pelo site e pelo APK, com integridade byte a byte conferida no build. `index.html` muda intencionalmente para oferecer a mesma metodologia nos dois ambientes; as três consultas de preços permanecem inalteradas. Largura/tema/fontes continuam restritos ao asset Android.

Custo classificado por item, em centavos:

```
vProd − vDesc − ICMS desonerado efetivamente deduzido
+ ICMS-ST + FCP-ST + IPI tributado + IPI devolvido
+ frete + seguro + outras despesas + II
+ PIS-ST/COFINS-ST com indicador de soma = 1
+ ICMS monofásico a reter
```

ICMS normal, PIS/COFINS informativos, tributos já retidos/embutidos e bases de cálculo não são acrescentados por cima. `IPI/IPINT` é zero conhecido, não alvo de rateio de IPI tributado. CSTs sem ST não recebem ST por ausência de campo. `vIPIDevol` não é multiplicado novamente por pDevol. O crédito fiscal recuperável não pode ser determinado pelo XML do fornecedor: o custo exibido inclui tributos cobrados **antes de créditos**, e créditos somente são abatidos quando informados e confirmados pelo usuário, em campo separado. Não há determinação automática de regime ou direito a crédito.

Valores financeiros de XML são convertidos por aritmética decimal/BigInt; totais e rateios usam centavos inteiros. Quantidades e fatores são decimais positivos, com limites de precisão. A divisão e o markup são feitos sobre a base **não arredondada**; arredonda-se o valor final em centavos, metade para cima. Não se aplica 20% sobre um custo já arredondado, nem se chama acréscimo de margem líquida.

## Conciliação e rateio

- A presença do campo é preservada: ausente é diferente de zero explícito.
- O total oficial e a soma explícita de cada componente são comparados. **Somente o residual positivo** pode ser rateado, apenas entre itens incluídos (`indTot=1`) sem aquele campo.
- Critério: proporção do vProd, maior resto em centavos, desempate pela ordem documental. A soma dos centavos coincide exatamente com o residual. Sem base positiva, não se inventa rateio.
- Campos conhecidos, inclusive zeros, nunca são escalonados para forçar coincidência. Total menor que itens, diferença sem item elegível ou vProd incoerente geram pendência.
- Rateio é identificado como **analítico**, não como imposto individual discriminado no XML; aparece nos detalhes e no CSV. IPI/ST e outras parcelas não são somados duas vezes quando aparecem no item e nos totais.
- O valor classificado é conciliado com vNF antes de custos adicionais/créditos manuais. A diferença de R$ 2,34 do relato não é rotulada IPI: sem o XML não é possível auditá-la.
- Despesas fora do XML e créditos confirmados são ajustes separados, rateados analiticamente por vProd; não alteram vNF nem o arquivo original. O usuário precisa confirmar que a atribuição analítica se aplica à sua compra. Não equivalem a apuração tributária por produto.
- Item `indTot=0` não recebe rateio nem preço final de aquisição; componentes positivos nele exigem classificação. Finalidade complementar/ajuste/devolução e importação exigem confirmação da natureza de aquisição, sem remover divergências reais.
- Desoneração sem indicador aplicável, serviços mistos e faturamento direto de veículos permanecem pendentes quando não é possível classificar. O app não finge autorizar/validar XML junto à SEFAZ.

**Preço por unidade só aparece quando o custo está classificado/conciliado, a quantidade é válida e a conversão está confirmada.** Custo por unidade comercial e valores originais continuam auditáveis. Divergências não são reparadas com “IPI implícito” nem com ajuste automático escondido.

## Embalagens, quantidades e preferências

- UN e aliases inequívocos usam fator 1; divergência entre quantidades comerciais/tributáveis ambas em UN exige confirmação. KG/G/L/ML/M e demais unidades a granel permanecem explicitamente por essa unidade, sem virar peças.
- CX/FD/PCT e unidades desconhecidas exigem confirmação. uTrib=UN, qTrib/qCom e vUnTrib compatíveis podem sugerir um fator, mas unidade tributável **não é presumida unidade vendável**.
- Números em xProd nunca determinam automaticamente a conversão. A descrição e os valores documentais completos (incluindo vUnCom/vUnTrib) ficam nos detalhes.
- O usuário informa fator positivo e unidade final, e marca a confirmação. Mudanças num fator já confirmado recalculam imediatamente quantidade, custos, IPI/ST por unidade e preço. Zero, negativo, vazio e precisão excessiva não geram divisão inválida/preço enganoso.
- Preferências por fornecedor + código do produto (ou GTIN) + unidade comercial ficam no armazenamento local. Sem identificador estável, a preferência fica restrita à nota/item, evitando aplicar uma conversão a outro produto só pelo nome.
- As opções da nota (extra/crédito/classificação) são separadas dos fatores de produto. A sessão guarda dados documentais e opções; o XML original nunca é regravado.
- Sessões antigas sem os novos componentes ficam com preço pendente e pedido de reimportação. O mesmo XML atualiza os dados antigos sem duplicar abas. Divergência de conteúdo fiscal não substitui a coleção silenciosamente: exige Nova consulta.

## Tabela e apresentação

São preservados os 16 campos, ordem, arrastar, visibilidade 14/16 e scroll. Cada célula acompanha sua chave de coluna. No Android um colgroup é recalculado por fonte, cabeçalhos, valores, visibilidade e ordem: Item/Quantidade usam o espaço natural necessário, não mínimos fixos de 6/12 em; Produto recebe mais espaço, limitado à área útil quando possível. Cabeçalhos curtos permanecem inteiros. Nomes de produtos quebram por palavras, sem truncar. Fontes não foram reduzidas.

A tabela deixa custo final e preço por unidade visíveis, com unidade explícita. Embalagem, fator, total de unidades, IPI/ST/despesas/descontos e rateios ficam em detalhes expansíveis por produto. IPI total é mostrado separadamente; a decomposição completa da nota fica expansível, com alertas de pendência sempre visíveis. O desconto oficial positivo permanece verde.

CSV usa o mesmo motor e inclui componentes, conversão/confirmação, ajustes, unidade, markup, vNF, conciliação e rateios. Preço pendente é vazio, acompanhado da razão. A impressão inclui resumo auditável de cada item mesmo com detalhes fechados; campos de edição não são impressos.

## Referências oficiais consultadas

Consulta feita em 27/10/2026, por download do [portal oficial SVRS](https://dfe-portal.svrs.rs.gov.br/Nfe/Documentos):

- [MOC 7.0 Anexo I](https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico/?sistema=NFE&tipoArquivo=1&nomeArquivo=moc7-anexo-i-leiaute-e-rv.pdf), W16-10, pp. 121–122: componentes do total e exceções.
- NT 2020.005 v1.20: PIS-ST/COFINS-ST somente com indSomaPISST/indSomaCOFINSST=1; não PIS/COFINS normais.
- NT 2023.004 v1.11: indDeduzDeson e correções/exceções de W16-10; monofásico a reter.
- NT 2025.002 v1.52, VB01/W60, e NT 2026.008 v1.00: novos totais/vItem e exceção de não acrescentar IBS/CBS/IS em 2025/2026. Alguns dispositivos estão marcados como implementação futura no próprio documento.

O motor não adiciona IBS/CBS/IS por cima de vProd em documentos de 2025/2026 identificados pela data do XML; informa a metodologia. vItem e vNFTot, se presentes, também precisam conciliar. Ano/regra não suportado fica pendente; não é aplicada automaticamente a regra futura de 2027. vProdLiq não é usado como substituto fictício do desembolso, nem como crédito tributário automático.

Não foi disponibilizado o XML real da nota do proprietário: não se afirma qual tributo causou a diferença fotografada, nem que estes testes representam validação legal de uma NF-e autorizada.

## Validação

- `npm test`: 28 testes JavaScript, sendo 10 do motor decimal/rateios e 18 no navegador. Fixtures com e sem IPI, IPINT, ST/FCP-ST, II/IPI devolvido, despesas/descontos por item/total, zeros, divergências, frações, UN/CX/FD/PCT/KG, qTrib, markup 0/20/customizado, CSV real, mídia print, múltiplas notas, migração e persistência.
- 8 unitários Java e 5 testes da política de assinatura.
- 30 instrumentados esperados: os 27 anteriores (incluindo SAF real 2/3/5/7, scanner ML Kit/OCR offline e acessibilidade) mais FinancialTest (cálculos/conversão persistida; divergência/CSV; larguras medidas e screenshots 90/100/140/160%, claro/escuro, retrato/paisagem, 14/16 e reordenação). Métricas ficam em financial-layout-metrics.json no artefato nativo.
- Lint, Actions do commit final, download do APK público, identidade original, assinatura, ABIs, checksum e bytes dos assets são conferidos antes da aprovação; os resultados reais ficam na issue #1 e release 1.3.0.

Limites: emulador e XMLs sintéticos não substituem o XML real ausente, telefone físico, providers OEM/nuvem, foco/flash físicos, impressão em papel, apuração fiscal ou APIs oficiais ao vivo. Assinatura debug pode mudar e impedir instalar sobre outra assinatura; não há desinstalação/limpeza automática. APKs anteriores são preservados.
