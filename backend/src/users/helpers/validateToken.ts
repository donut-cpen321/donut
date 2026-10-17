const { OAuth2Client } = require('google-auth-library');

export async function validateToken(idToken: string) {

    // Initialize the client with your Google Cloud Client ID
    const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

    try {
        const ticket = await client.verifyIdToken({
            idToken: idToken,
            audience: process.env.GOOGLE_CLIENT_ID,
        });
        
        const payload = await ticket.getPayload();

        return payload;
    } catch (error) {
        return null;
    }
}