param nameSuffix string
param location string = resourceGroup().location

resource account 'Microsoft.CognitiveServices/accounts@2023-05-01' = {
  name: 'openai-${nameSuffix}'
  location: location
  kind: 'OpenAI'
  properties: {
    publicNetworkAccess: 'Enabled'
    customSubDomainName: 'openai-msday-demo-${nameSuffix}'
  }
  sku: {
    name: 'S0'
  }
}

resource chat_deployment 'Microsoft.CognitiveServices/accounts/deployments@2023-05-01' = {
  name: 'gpt-35-turbo'
  parent: account
  sku: {
    name: 'Standard'
    capacity: 50
  }
  properties: {
    model: {
      format: 'OpenAI'
      name: 'gpt-35-turbo'
      version: '0613'
    }
  }
}

resource instruct_deployment 'Microsoft.CognitiveServices/accounts/deployments@2023-05-01' = {
  name: 'gpt-35-turbo-instruct'
  parent: account
  sku: {
    name: 'Standard'
    capacity: 50
  }
  properties: {
    model: {
      format: 'OpenAI'
      name: 'gpt-35-turbo-instruct'
      version: '0914'
    }
  }
}

resource embedding_deployment 'Microsoft.CognitiveServices/accounts/deployments@2023-05-01' = {
  name: 'text-embedding-ada-002'
  parent: account
  sku: {
    name: 'Standard'
    capacity: 50
  }
  properties: {
    model: {
      format: 'OpenAI'
      name: 'text-embedding-ada-002'
      version: '2'
    }
  }
}
