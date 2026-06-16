import { useParams } from 'react-router-dom'

export default function TransferDetail() {
  const { transferId } = useParams<{ transferId: string }>()

  return (
    <section>
      <p>Transfer ID: {transferId}</p>
      <p>This page is under construction.</p>
    </section>
  )
}
