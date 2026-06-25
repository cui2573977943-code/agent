function stanceClass(stance) {
  if (!stance) return ''
  const s = stance.toUpperCase()
  if (s === 'UP' || s === 'AGGRESSIVE') return 'pos'
  if (s === 'DOWN' || s === 'CONSERVATIVE') return 'neg'
  return 'muted'
}

function Node({ node, root }) {
  return (
    <div className={'tree-node' + (root ? ' tree-root' : '')}>
      <div className="tree-title">
        {node.type === 'DIMENSION' ? '🌿 ' : node.type === 'THOUGHT' ? '• ' : '🌳 '}
        {node.title}
      </div>
      {(node.stance || node.score != null || node.content) && (
        <div className="tree-meta">
          {node.stance && (
            <span className={stanceClass(node.stance)}>[{node.stance}] </span>
          )}
          {node.score != null && <span>评分 {Number(node.score).toFixed(2)} · </span>}
          {node.content}
        </div>
      )}
      {node.children &&
        node.children.map((c, i) => <Node key={i} node={c} />)}
    </div>
  )
}

export default function ThoughtTree({ tree }) {
  if (!tree) return null
  return <Node node={tree} root />
}
