import { test, expect } from '@playwright/test';

/**
 * Teste funcional da calculadora de frete (/frete).
 *
 * Regras declaradas na própria página:
 *  - CEP precisa ter exatamente 8 dígitos;
 *  - valor do pedido precisa ser positivo, com no máximo 2 casas decimais;
 *  - CEP iniciado por 8 ............ R$ 15,00;
 *  - demais CEPs .................. R$ 25,00;
 *  - pedidos a partir de R$ 200,00 . frete grátis.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */

type Caso = {
  cep: string;
  valor: string;
  esperado: string;
  classe: string;
};

/** Caminhos válidos: cada linha cobre uma classe de equivalência ou um valor-limite. */
const casosValidos: Caso[] = [
  { cep: '80000000', valor: '10,00',  esperado: 'Frete: R$ 15,00', classe: 'CEP da faixa 8 com valor abaixo do frete grátis' },
  { cep: '89999999', valor: '199,99', esperado: 'Frete: R$ 15,00', classe: 'limite inferior: R$ 0,01 abaixo do frete grátis' },
  { cep: '01001000', valor: '10,00',  esperado: 'Frete: R$ 25,00', classe: 'CEP fora da faixa 8 (demais CEPs)' },
  { cep: '99999999', valor: '199,99', esperado: 'Frete: R$ 25,00', classe: 'demais CEPs no limite inferior do frete grátis' },
  { cep: '80000000', valor: '200,00', esperado: 'Frete grátis',    classe: 'limite exato do frete grátis com CEP 8' },
  { cep: '01001000', valor: '200,00', esperado: 'Frete grátis',    classe: 'limite exato do frete grátis com demais CEPs' },
  { cep: '01001000', valor: '200,01', esperado: 'Frete grátis',    classe: 'acima do limite do frete grátis' },
  { cep: '80000000', valor: '0,01',   esperado: 'Frete: R$ 15,00', classe: 'menor valor positivo aceito' },
  { cep: '80000000', valor: '150.50', esperado: 'Frete: R$ 15,00', classe: 'ponto aceito como separador decimal' },
  { cep: '80000000', valor: '150',    esperado: 'Frete: R$ 15,00', classe: 'valor inteiro, sem casas decimais' },
  { cep: ' 80000000 ', valor: ' 10,00 ', esperado: 'Frete: R$ 15,00', classe: 'espaços em volta são removidos antes da validação' },
];

/** Classes inválidas: a página responde sempre com a mesma mensagem genérica. */
const casosInvalidos: Caso[] = [
  { cep: '8000000',   valor: '10,00',  esperado: 'Dados inválidos', classe: 'CEP com 7 dígitos (limite inferior - 1)' },
  { cep: '800000000', valor: '10,00',  esperado: 'Dados inválidos', classe: 'CEP com 9 dígitos (limite superior + 1)' },
  { cep: '8000000A',  valor: '10,00',  esperado: 'Dados inválidos', classe: 'CEP com caractere não numérico' },
  { cep: '80000-000', valor: '10,00',  esperado: 'Dados inválidos', classe: 'CEP formatado com hífen' },
  { cep: '',          valor: '10,00',  esperado: 'Dados inválidos', classe: 'CEP vazio' },
  { cep: '80000000',  valor: '0',      esperado: 'Dados inválidos', classe: 'valor zero (não é positivo)' },
  { cep: '80000000',  valor: '0,00',   esperado: 'Dados inválidos', classe: 'valor zero com casas decimais' },
  { cep: '80000000',  valor: '-10,00', esperado: 'Dados inválidos', classe: 'valor negativo' },
  { cep: '80000000',  valor: '10,123', esperado: 'Dados inválidos', classe: 'valor com 3 casas decimais' },
  { cep: '80000000',  valor: 'abc',    esperado: 'Dados inválidos', classe: 'valor não numérico' },
  { cep: '80000000',  valor: '',       esperado: 'Dados inválidos', classe: 'valor vazio' },
  { cep: '',          valor: '',       esperado: 'Dados inválidos', classe: 'formulário totalmente vazio' },
];

async function calcular(page: import('@playwright/test').Page, cep: string, valor: string) {
  await page.goto('/frete');
  await page.getByLabel('CEP').fill(cep);
  await page.getByLabel('Valor do pedido').fill(valor);
  await page.getByRole('button', { name: 'Calcular frete' }).click();
  return page.locator('#resultado');
}

test.describe('calculadora de frete — caminhos válidos', () => {
  for (const caso of casosValidos) {
    test(`CEP ${caso.cep.trim()} e valor ${caso.valor.trim()} — ${caso.classe}`, async ({ page }) => {
      const resultado = await calcular(page, caso.cep, caso.valor);

      await expect(resultado).toBeVisible();
      await expect(resultado).toHaveText(caso.esperado);
      await expect(resultado).toHaveAttribute('role', 'status');
      await expect(resultado).toHaveClass('success');
    });
  }
});

test.describe('calculadora de frete — classes inválidas', () => {
  for (const caso of casosInvalidos) {
    test(`CEP "${caso.cep}" e valor "${caso.valor}" — ${caso.classe}`, async ({ page }) => {
      const resultado = await calcular(page, caso.cep, caso.valor);

      await expect(resultado).toBeVisible();
      await expect(resultado).toHaveText('Dados inválidos');
      await expect(resultado).toHaveAttribute('role', 'alert');
      await expect(resultado).not.toHaveClass('success');
    });
  }
});

test.describe('calculadora de frete — comportamento da interface', () => {
  test('não exibe resultado antes do primeiro cálculo', async ({ page }) => {
    await page.goto('/frete');

    await expect(page.locator('#resultado')).toBeHidden();
  });

  test('recalcula e substitui o resultado anterior', async ({ page }) => {
    const resultado = await calcular(page, '80000000', '10,00');
    await expect(resultado).toHaveText('Frete: R$ 15,00');

    // Mesmo CEP, valor agora no limite do frete grátis: o resultado deve ser substituído.
    await page.getByLabel('Valor do pedido').fill('200,00');
    await page.getByRole('button', { name: 'Calcular frete' }).click();

    await expect(resultado).toHaveText('Frete grátis');
  });

  test('erro anterior é substituído por um cálculo válido', async ({ page }) => {
    const resultado = await calcular(page, '8000000', '10,00');
    await expect(resultado).toHaveText('Dados inválidos');

    await page.getByLabel('CEP').fill('01001000');
    await page.getByRole('button', { name: 'Calcular frete' }).click();

    await expect(resultado).toHaveText('Frete: R$ 25,00');
    await expect(resultado).toHaveAttribute('role', 'status');
  });

  test('link de retorno leva de volta ao login', async ({ page }) => {
    await page.goto('/frete');
    await page.getByRole('link', { name: 'Voltar ao início' }).click();

    await expect(page).toHaveURL(/\/login$/);
  });
});
